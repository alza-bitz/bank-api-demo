(ns bank.persistence.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defprotocol AccountRepository
  "Repository interface for account operations."
  (create-account [this account-name])
  (find-account [this account-number])
  (save-account-event [this account event]))

(def rs->account rs/as-unqualified-kebab-maps)

(def rs->account-event rs/as-unqualified-kebab-maps)

(defrecord JdbcAccountRepository [datasource]
  AccountRepository

  (create-account [_ name]
    (jdbc/with-transaction [tx datasource]
      (sql/insert! tx :account {:name name} {:return-keys true
                                             :builder-fn rs->account})))

  (find-account [_ account-number]
    (sql/get-by-id datasource :account account-number :account_number {:builder-fn rs->account}))

  (save-account-event [_ account event]
    (jdbc/with-transaction [tx datasource]
      ;; First update the account
      (sql/update! tx :account
                   (select-keys account [:balance])
                   {:account_number (:account-number account)})
      ;; Then insert the event
      (sql/insert! tx :account_event
                   {:account_number (:account-number event)
                    :description (:description event)}
                   {:return-keys true
                    :builder-fn rs->account-event}))))

;; Database schema functions
(defn create-tables!
  "Creates the account and account_event tables."
  [datasource]
  (jdbc/execute! datasource
                 ["CREATE TABLE IF NOT EXISTS account (
        account_number SERIAL PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        balance INTEGER NOT NULL DEFAULT 0
      )"])
  (jdbc/execute! datasource
                 ["CREATE TABLE IF NOT EXISTS account_event (
        event_id SERIAL PRIMARY KEY,
        account_number INTEGER NOT NULL REFERENCES account(account_number),
        description VARCHAR(255) NOT NULL,
        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )"]))

(defn drop-tables!
  "Drops the account and account_event tables."
  [datasource]
  (jdbc/execute! datasource ["DROP TABLE IF EXISTS account_event"])
  (jdbc/execute! datasource ["DROP TABLE IF EXISTS account"]))

(defn logging-jdbc-account-repository
  "Creates a JdbcAccountRepository with logging wrapped datasource."
  [datasource]
  (->JdbcAccountRepository
   (jdbc/with-logging datasource #(log/info "SQL:" %1 "Params:" %2))))

;; Integrant methods
(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (logging-jdbc-account-repository datasource))

(defmethod ig/halt-key! ::repository [_ _]
  ;; No cleanup needed for repository
  nil)
