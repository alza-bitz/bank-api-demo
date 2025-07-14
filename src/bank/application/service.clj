(ns bank.application.service
  (:require [bank.domain.account :as domain]
            [bank.persistence.repository :as repo]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [>! <! >!! <!! chan go go-loop close! alts!! timeout]]))

(defprotocol AccountService
  "Application service interface for account operations."
  (create-account [this name])
  (retrieve-account [this account-number])
  (deposit-to-account [this account-number amount])
  (withdraw-from-account [this account-number amount])
  (transfer-between-accounts [this sender-account-number receiver-account-number amount])
  (retrieve-account-audit [this account-number]))

(defn- do-create-account
  "Shared implementation for account creation."
  [repository name]
  (log/info "Creating account for" name)
  (let [account (domain/create-account name)]
    (repo/save-account repository account)))

(defn- do-retrieve-account
  "Shared implementation for account retrieval."
  [repository account-number]
  (log/info "Retrieving account" account-number)
  (repo/find-account repository account-number))

(defn- do-deposit-to-account
  "Shared implementation for account deposit."
  [repository account-number amount]
  (log/info "Depositing" amount "to account" account-number)
  (let [account (repo/find-account repository account-number)
        {updated-account :account deposit-event :event} (domain/deposit account amount)]
    (repo/save-account-event repository updated-account deposit-event)
    updated-account))

(defn- do-withdraw-from-account
  "Shared implementation for account withdrawal."
  [repository account-number amount]
  (log/info "Withdrawing" amount "from account" account-number)
  (let [account (repo/find-account repository account-number)
        {updated-account :account withdraw-event :event} (domain/withdraw account amount)]
    (repo/save-account-event repository updated-account withdraw-event)
    updated-account))

(defn- do-transfer-between-accounts
  "Shared implementation for account transfer."
  [repository sender-account-number receiver-account-number amount]
  (log/info "Transferring" amount "from account" sender-account-number "to account" receiver-account-number)
  (let [sender-account (repo/find-account repository sender-account-number)
        receiver-account (repo/find-account repository receiver-account-number)
        {:keys [sender receiver]} (domain/transfer sender-account receiver-account amount)
        account-event-pairs [{:account (:account sender) :event (:event sender)}
                             {:account (:account receiver) :event (:event receiver)}]]
    (repo/save-account-events repository account-event-pairs)
    {:sender (:account sender)
     :receiver (:account receiver)}))

(defn- do-retrieve-account-audit
  "Shared implementation for account audit retrieval."
  [repository account-number]
  (log/info "Retrieving audit log for account" account-number) 
  (let [found-account (repo/find-account repository account-number)]
    (repo/find-account-events repository (:account-number found-account))))

(defrecord SyncAccountService [repository]
  AccountService

  (create-account [_ name]
    (do-create-account repository name))

  (retrieve-account [_ account-number]
    (do-retrieve-account repository account-number))

  (deposit-to-account [_ account-number amount]
    (do-deposit-to-account repository account-number amount))

  (withdraw-from-account [_ account-number amount]
    (do-withdraw-from-account repository account-number amount))

  (transfer-between-accounts [_ sender-account-number receiver-account-number amount]
    (do-transfer-between-accounts repository sender-account-number receiver-account-number amount))

  (retrieve-account-audit [_ account-number]
    (do-retrieve-account-audit repository account-number)))

;; Integrant methods
(defmethod ig/init-key ::service [_ {:keys [repository]}]
  (->SyncAccountService repository))

(defmethod ig/halt-key! ::service [_ _]
  ;; No cleanup needed for service
  nil)

(defprotocol AsyncOperationProducer
  "Protocol for async operation handling."
  (submit-operation [this operation-fn])
  (retrieve-operation-result [this operation-id]))

;; Operation data structures
(defrecord Operation [id operation-fn result-channel])
(defrecord OperationResult [result exception])

;; Consumer functions
(defn- start-operation-consumer
  "Starts a single operation consumer that processes operations from the operation channel."
  [operation-channel]
  (go-loop []
    (when-let [operation (<! operation-channel)]
      (let [result (try
                     (->OperationResult ((:operation-fn operation)) nil)
                     (catch Exception e
                       (->OperationResult nil e)))]
        (>! (:result-channel operation) result))
      (recur))))

(defn stop
  "Stops the async service by closing all channels."
  [{:keys [state]}]
  (when-let [operation-channel (:operation-channel @state)]
    (close! operation-channel))
  ;; Close all result channels
  (doseq [result-channel (vals (:result-channels @state))]
    (close! result-channel))
  (swap! state assoc
         :operation-channel nil
         :result-channels {}))

(defrecord AsyncAccountService [repository state]
  AccountService

  (create-account [_ name]
    (submit-operation _ #(do-create-account repository name)))

  (retrieve-account [_ account-number]
    (submit-operation _ #(do-retrieve-account repository account-number)))

  (deposit-to-account [_ account-number amount]
    (submit-operation _ #(do-deposit-to-account repository account-number amount)))

  (withdraw-from-account [_ account-number amount]
    (submit-operation _ #(do-withdraw-from-account repository account-number amount)))

  (transfer-between-accounts [_ sender-account-number receiver-account-number amount]
    (submit-operation _ #(do-transfer-between-accounts repository sender-account-number receiver-account-number amount)))

  (retrieve-account-audit [_ account-number]
    (submit-operation _ #(do-retrieve-account-audit repository account-number)))

  AsyncOperationProducer

  (submit-operation [_ operation-fn]
    (let [operation-id (random-uuid)
          result-channel (chan 1)
          operation (->Operation operation-id operation-fn result-channel)
          current-state @state]
      (swap! state update :result-channels assoc operation-id result-channel)
      (>!! (:operation-channel current-state) operation)
      operation-id))

  (retrieve-operation-result [_ operation-id]
    (let [result-channels (:result-channels @state)
          result-channel (get result-channels operation-id)
          timeout-ch (timeout 30000)] ; 30 second timeout
      (if result-channel
        (let [[result ch] (alts!! [result-channel timeout-ch])]
          (cond
            (= ch timeout-ch)
            (do
              ;; Clean up the result channel registry
              (swap! state update :result-channels dissoc operation-id)
              (close! result-channel)
              (throw (ex-info "Operation timeout" {:operation-id operation-id})))

            :else
            (do
              ;; Clean up the result channel registry
              (swap! state update :result-channels dissoc operation-id)
              (close! result-channel)
              ;; Return the result or throw exception
              (if (:exception result)
                (throw (:exception result))
                (:result result)))))
        (throw (ex-info "Operation not found" {:operation-id operation-id}))))))

(defn consumer-pool-async-account-service
  "Factory function to create AsyncAccountService with internal state management."
  [repository consumer-pool-size]
  (let [operation-channel (chan 1000)  ; Buffer to handle up to 1000 operations
        _ (doall
           (for [_ (range consumer-pool-size)]
             (start-operation-consumer operation-channel)))]
    (->AsyncAccountService repository (atom {:operation-channel operation-channel
                                             :result-channels {}}))))

;; Integrant methods for async service
(defmethod ig/init-key ::async-service [_ {:keys [repository pool-size]
                                           :or {pool-size 10}}]
  (consumer-pool-async-account-service repository pool-size))

(defmethod ig/halt-key! ::async-service [_ async-service]
  (stop async-service))
