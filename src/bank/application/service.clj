(ns bank.application.service
  (:require [bank.domain.account :as domain]
            [bank.persistence.repository :as repo]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]))

(defprotocol AccountService
  "Application service interface for account operations."
  (create-account [this name])
  (get-account [this account-number])
  (deposit-to-account [this account-number amount]))

(defrecord DefaultAccountService [repository]
  AccountService

  (create-account [_ name]
    (log/info "Creating account for" name)
    (let [account (domain/create-account name)]
      (repo/save-account repository account)))

  (get-account [_ account-number]
    (log/info "Getting account" account-number)
    (repo/find-account repository account-number))

  (deposit-to-account [_ account-number amount]
    (log/info "Depositing" amount "to account" account-number)
    (if-let [account (repo/find-account repository account-number)]
      (let [[updated-account deposit-event] (domain/deposit account amount)]
        (repo/save-account-event repository updated-account deposit-event)
        updated-account)
      (throw (ex-info "Account not found" {:account-number account-number})))))

;; Integrant methods
(defmethod ig/init-key ::service [_ {:keys [repository]}]
  (->DefaultAccountService repository))

(defmethod ig/halt-key! ::service [_ _]
  ;; No cleanup needed for service
  nil)
