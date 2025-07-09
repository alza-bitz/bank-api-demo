(ns bank.persistence.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [integrant.core :as ig]))

(defprotocol AccountRepository
  "Repository interface for account operations."
  (create-account [this account-data])
  (find-account [this account-number])
  (save-account-event [this account event]))

(defrecord JdbcAccountRepository [datasource]
  AccountRepository

  (create-account [_ {:keys [name]}]
    (let [logged-ds (jdbc/with-logging datasource #(log/info "SQL:" %1 "Params:" %2))]
      (jdbc/with-transaction [tx logged-ds]
        (let [result (sql/insert! tx :account {:name name}
                                  {:return-keys true
                                   :builder-fn rs/as-unqualified-lower-maps})
              account-number (:account_number result)
              balance (:balance result)]
          {:account-number account-number
           :name name
           :balance balance}))))

  (find-account [_ account-number]
    (let [logged-ds (jdbc/with-logging datasource #(log/info "SQL:" %1 "Params:" %2))]
      (when-let [result (sql/get-by-id logged-ds :account account-number :account_number
                                       {:builder-fn rs/as-unqualified-lower-maps})]
        (-> result
            (update :account_number int)
            (update :balance int)
            (set/rename-keys {:account_number :account-number})))))

  (save-account-event [_ account event]
    (let [logged-ds (jdbc/with-logging datasource #(log/info "SQL:" %1 "Params:" %2))]
      (jdbc/with-transaction [tx logged-ds]
        ;; First update the account
        (sql/update! tx :account
                     (select-keys account [:balance])
                     {:account_number (:account-number account)})
        ;; Then insert the event
        (sql/insert! tx :account_event
                     {:account_number (:account-number event)
                      :event_type (name (:event-type event))
                      :event_data (pr-str (:event-data event))
                      :timestamp (:timestamp event)}
                     {:return-keys true})))))

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
        event_type VARCHAR(50) NOT NULL,
        event_data TEXT,
        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )"]))

(defn drop-tables!
  "Drops the account and account_event tables."
  [datasource]
  (jdbc/execute! datasource ["DROP TABLE IF EXISTS account_event"])
  (jdbc/execute! datasource ["DROP TABLE IF EXISTS account"]))

;; Integrant methods
(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->JdbcAccountRepository datasource))

(defmethod ig/halt-key! ::repository [_ _]
  ;; No cleanup needed for repository
  nil)
