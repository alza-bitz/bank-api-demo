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
    (repo/find-account repository account-number))

  (deposit-to-account [_ account-number amount]
    (log/info "Depositing" amount "to account" account-number)
    (let [account (repo/find-account repository account-number)
          {updated-account :account  deposit-event :event} (domain/deposit account amount)]
      (repo/save-account-event repository updated-account deposit-event)
      updated-account))

  (withdraw-from-account [_ account-number amount]
    (log/info "Withdrawing" amount "from account" account-number)
    (let [account (repo/find-account repository account-number)
          {updated-account :account withdraw-event :event} (domain/withdraw account amount)]
      (repo/save-account-event repository updated-account withdraw-event)
      updated-account)))

;; Integrant methods
(defmethod ig/init-key ::service [_ {:keys [repository]}]
  (->SyncAccountService repository))

(defmethod ig/halt-key! ::service [_ _]
  ;; No cleanup needed for service
  nil)
