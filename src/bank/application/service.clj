(ns bank.application.service
  (:require [bank.domain.account :as domain]
            [bank.persistence.repository :as repo]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]))

(defprotocol AccountService
  "Application service interface for account operations."
  (create-account [this name])
  (retrieve-account [this account-number])
  (deposit-to-account [this account-number amount])
  (withdraw-from-account [this account-number amount]))

(defrecord SyncAccountService [repository]
  AccountService

  (create-account [_ name]
    (log/info "Creating account for" name)
    (let [account (domain/create-account name)]
      (repo/save-account repository account)))

  (retrieve-account [_ account-number]
    (log/info "Retrieving account" account-number)
    (or (repo/find-account repository account-number)
        (throw (ex-info "Account not found" {:account-number account-number}))))

  (deposit-to-account [_ account-number amount]
    (log/info "Depositing" amount "to account" account-number)
    (if-let [account (repo/find-account repository account-number)]
      (let [{updated-account :account  deposit-event :event} (domain/deposit account amount)]
        (repo/save-account-event repository updated-account deposit-event)
        updated-account)
      (throw (ex-info "Account not found" {:account-number account-number}))))

  (withdraw-from-account [_ account-number amount]
    (log/info "Withdrawing" amount "from account" account-number)
    (if-let [account (repo/find-account repository account-number)]
      (if (>= (:balance account) amount)
        (let [{updated-account :account withdraw-event :event} (domain/withdraw account amount)]
          (repo/save-account-event repository updated-account withdraw-event)
          updated-account)
        (throw (ex-info "Insufficient funds" {:account-number account-number :balance (:balance account) :amount amount})))
      (throw (ex-info "Account not found" {:account-number account-number})))))

;; Integrant methods
(defmethod ig/init-key ::service [_ {:keys [repository]}]
  (->SyncAccountService repository))

(defmethod ig/halt-key! ::service [_ _]
  ;; No cleanup needed for service
  nil)
