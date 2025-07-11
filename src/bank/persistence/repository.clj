(ns bank.persistence.repository
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defprotocol AccountRepository
  "Repository interface for account operations."
  (save-account [this account])
  (find-account [this account-number])
  (save-account-event [this account event]))

(def rs->account rs/as-unqualified-kebab-maps)

(defn rs->account-event 
  "Transform result set for account events, converting event_sequence to sequence"
  [result-set options]
  (let [label-fn (fn [label]
                   (if (= "event_sequence" label)
                     "sequence"
                     (str/replace label #"_" "-")))]
    (rs/as-unqualified-modified-maps result-set (assoc options :label-fn label-fn))))

(defrecord JdbcAccountRepository [datasource]
  AccountRepository

  (save-account [_ account]
    (jdbc/with-transaction [tx datasource]
      (sql/insert! tx :account account {:return-keys true
                                        :builder-fn rs->account})))

  (find-account [_ account-number]
    (sql/get-by-id datasource :account account-number :account_number {:builder-fn rs->account}))

  (save-account-event [_ account event]
    (let [max-attempts 3
          retry-delay-range 50]
      (loop [attempt 1]
        (let [result (try
                       (when (> attempt 1)
                         (log/infof "Retrying transaction, attempt %d" attempt))
                       (jdbc/with-transaction [tx datasource]
                         ;; First update the account
                         (sql/update! tx :account
                                      (select-keys account [:balance])
                                      {:account_number (:account-number account)})
                         ;; Then insert the event using per-account sequence number
                         (jdbc/execute-one! tx
                                            ["INSERT INTO account_event (id, event_sequence, account_number, description, timestamp, debit, credit) 
                                              VALUES (?, (SELECT COALESCE(MAX(event_sequence), 0) + 1 FROM account_event WHERE account_number = ?), ?, ?, ?, ?, ?)"
                                             (:id event)
                                             (:account-number account)
                                             (:account-number account)
                                             (:description event)
                                             (java.sql.Timestamp/from (:timestamp event))
                                             (:debit (:action event))
                                             (:credit (:action event))]
                                            {:return-keys true
                                             :builder-fn rs->account-event}))
                       (catch org.postgresql.util.PSQLException e
                         (log/warnf "Failed to commit transaction on attempt %d: %s" attempt (.getMessage e))
                         (if (and (< attempt max-attempts)
                                  (= (.getState org.postgresql.util.PSQLState/UNIQUE_VIOLATION) 
                                     (.getSQLState e)))
                           (do
                             (Thread/sleep (rand-int retry-delay-range))
                             ::retry)
                           (do
                             (log/errorf "Failed to commit transaction after maximum %d attempts, rethrowing: %s" attempt (.getMessage e))
                             (throw e)))))]
          (if (= result ::retry)
            (recur (inc attempt))
            result))))))

;; Database schema functions
(defn create-tables!
  "Creates the account and account_event tables."
  [datasource]
  (let [logging-datasource (jdbc/with-logging datasource #(log/info {:op %1 :sql %2}))] 
    (jdbc/execute! logging-datasource
                   ["CREATE TABLE IF NOT EXISTS account (
        id UUID PRIMARY KEY,
        account_number SERIAL NOT NULL UNIQUE,
        name VARCHAR(255) NOT NULL,
        balance INTEGER NOT NULL
      )"])
    (jdbc/execute! logging-datasource
                   ["CREATE TABLE IF NOT EXISTS account_event (
        id UUID PRIMARY KEY,
        event_sequence INTEGER NOT NULL,
        account_number INTEGER NOT NULL REFERENCES account(account_number),
        description VARCHAR(255) NOT NULL,
        timestamp TIMESTAMP NOT NULL,
        debit INTEGER,
        credit INTEGER,
        UNIQUE(event_sequence, account_number)
      )"])))

(defn drop-tables!
  "Drops the account and account_event tables."
  [datasource]
  (let [logging-datasource (jdbc/with-logging datasource #(log/info {:op %1 :sql %2}))] 
    (jdbc/execute! logging-datasource ["DROP TABLE IF EXISTS account_event"])
    (jdbc/execute! logging-datasource ["DROP TABLE IF EXISTS account"])))

(defn logging-jdbc-account-repository
  "Creates a JdbcAccountRepository with logging wrapped datasource."
  [datasource]
  (->JdbcAccountRepository
   (jdbc/with-logging datasource #(log/info {:op %1 :sql %2}))))

;; Integrant methods
(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (logging-jdbc-account-repository datasource))

(defmethod ig/halt-key! ::repository [_ _]
  ;; No cleanup needed for repository
  nil)
