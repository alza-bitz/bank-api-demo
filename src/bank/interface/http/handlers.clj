(ns bank.interface.http.handlers
  (:require [bank.application.service :as service]
            [bank.interface.http.api :as api]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn create-account-handler
  "HTTP handler for creating bank accounts."
  [service]
  (fn [request]
    (try
      (let [body (:body-params request)
            name (:name body)]
        (log/info "HTTP: Creating account for" name)
        (let [account (service/create-account service name)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch Exception e
        (log/error e "HTTP: Error creating account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to create account"}}))))

(defn view-account-handler
  "HTTP handler for viewing bank accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Viewing account" account-number)
        (let [account (service/retrieve-account service account-number)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (let [error-data (ex-data e)
              error-key (:error error-data)]
          (case error-key
            :account-not-found
            (do
              (log/warn "HTTP: Account not found" (:account-number error-data))
              {:status 404
               :body {:error "account-not-found"
                      :message "Account not found"}})
            ;; default case
            (do
              (log/error e "HTTP: Error viewing account")
              {:status 500
               :body {:error "internal-server-error"
                      :message "Failed to retrieve account"}}))))
      (catch Exception e
        (log/error e "HTTP: Error viewing account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to retrieve account"}}))))

(defn deposit-handler
  "HTTP handler for depositing money to accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Depositing" amount "to account" account-number)
        (let [account (service/deposit-to-account service account-number amount)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (let [error-data (ex-data e)
              error-key (:error error-data)]
          (case error-key
            :account-not-found
            (do
              (log/warn "HTTP: Account not found" (:account-number error-data))
              {:status 404
               :body {:error "account-not-found"
                      :message "Account not found"}})
            ;; default case
            (do
              (log/error e "HTTP: Error depositing to account")
              {:status 500
               :body {:error "internal-server-error"
                      :message "Failed to deposit to account"}}))))
      (catch Exception e
        (log/error e "HTTP: Error depositing to account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to deposit to account"}}))))

(defn withdraw-handler
  "HTTP handler for withdrawing money from accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Withdrawing" amount "from account" account-number)
        (let [account (service/withdraw-from-account service account-number amount)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (let [error-data (ex-data e)
              error-key (:error error-data)]
          (case error-key
            :account-not-found
            (do
              (log/warn "HTTP: Account not found" (:account-number error-data))
              {:status 404
               :body {:error "account-not-found"
                      :message "Account not found"}})
            
            :insufficient-funds
            (do
              (log/warn "HTTP: Insufficient funds for withdrawal" error-data)
              {:status 422
               :body {:error "insufficient-funds"
                      :message "Insufficient funds for withdrawal"}})
            
            ;; default case
            (do
              (log/error e "HTTP: Error withdrawing from account")
              {:status 500
               :body {:error "internal-server-error"
                      :message "Failed to withdraw from account"}}))))
      (catch Exception e
        (log/error e "HTTP: Error withdrawing from account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to withdraw from account"}}))))

(defn transfer-handler
  "HTTP handler for transferring money between accounts."
  [service]
  (fn [request]
    (try
      (let [sender-account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)
            receiver-account-number (:account-number body)]
        (log/info "HTTP: Transferring" amount "from account" sender-account-number "to account" receiver-account-number)
        (let [result (service/transfer-between-accounts service sender-account-number receiver-account-number amount)
              sender-account (:sender result)
              response (api/account->response sender-account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (let [error-data (ex-data e)
              error-key (:error error-data)]
          (case error-key
            :account-not-found
            (do
              (log/warn "HTTP: Account not found" (:account-number error-data))
              {:status 404
               :body {:error "account-not-found"
                      :message "Account not found"}})
            
            :insufficient-funds
            (do
              (log/warn "HTTP: Insufficient funds for transfer" error-data)
              {:status 422
               :body {:error "insufficient-funds"
                      :message "Insufficient funds for transfer"}})
            
            :same-account-transfer
            (do
              (log/warn "HTTP: Cannot transfer to same account" error-data)
              {:status 422
               :body {:error "same-account-transfer"
                      :message "Cannot transfer to same account"}})
            
            ;; default case
            (do
              (log/error e "HTTP: Error transferring between accounts")
              {:status 500
               :body {:error "internal-server-error"
                      :message "Failed to transfer between accounts"}}))))
      (catch Exception e
        (log/error e "HTTP: Error transferring between accounts")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to transfer between accounts"}}))))

(defn audit-handler
  "HTTP handler for retrieving account audit logs."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Retrieving audit log for account" account-number)
        (let [events (service/retrieve-account-audit service account-number)
              response (mapv api/account-event->response events)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (let [error-data (ex-data e)
              error-key (:error error-data)]
          (case error-key
            :account-not-found
            (do
              (log/warn "HTTP: Account not found" (:account-number error-data))
              {:status 404
               :body {:error "account-not-found"
                      :message "Account not found"}})
            ;; default case
            (do
              (log/error e "HTTP: Error retrieving audit log")
              {:status 500
               :body {:error "internal-server-error"
                      :message "Failed to retrieve audit log"}}))))
      (catch Exception e
        (log/error e "HTTP: Error retrieving audit log")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to retrieve audit log"}}))))

(defrecord HttpHandlers [service]
  Object
  (toString [_] "HttpHandlers"))

(defn make-handlers
  "Creates HTTP handlers with service dependency."
  [service]
  {:create-account (create-account-handler service)
   :view-account (view-account-handler service)
   :deposit (deposit-handler service)
   :withdraw (withdraw-handler service)
   :transfer (transfer-handler service)
   :audit (audit-handler service)})

;; Integrant methods
(defmethod ig/init-key ::handlers [_ {:keys [service]}]
  (make-handlers service))

(defmethod ig/halt-key! ::handlers [_ _]
  ;; No cleanup needed for handlers
  nil)
