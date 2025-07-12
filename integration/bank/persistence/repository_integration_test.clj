(ns bank.persistence.repository-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [bank.persistence.repository :as repo]
            [bank.domain.account :as account])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def ^:dynamic *container* nil)

(use-fixtures :once
  (fn [f]
    (let [container (-> {:image-name "postgres:13"
                         :exposed-ports [5432]
                         :env-vars {"POSTGRES_DB" "testdb"
                                    "POSTGRES_USER" "testuser"
                                    "POSTGRES_PASSWORD" "testpass"}
                         :wait-for {:wait-strategy :port}}
                        tc/create
                        tc/start!)]
      (try
        (binding [*container* container]
          (f))
        (finally
          (tc/stop! container))))))

(defn ->datasource 
  [container]
  {:dbtype "postgresql"
   :host "localhost"
   :port (get (:mapped-ports container) 5432)
   :dbname "testdb"
   :user "testuser"
   :password "testpass"})

(defn ->connection-pool
  [container]
  (let [config (doto (HikariConfig.)
                 (.setJdbcUrl (str "jdbc:postgresql://localhost:"
                                   (get (:mapped-ports container) 5432)
                                   "/testdb"))
                 (.setUsername "testuser")
                 (.setPassword "testpass")
                 (.setMaximumPoolSize 20)
                 (.setMinimumIdle 5)
                 (.setConnectionTimeout 30000))]
    (HikariDataSource. config)))

(def ^:dynamic *datasource* nil)

(use-fixtures :each
  (fn [f]
    (let [datasource (->datasource *container*)]
      (try
        (repo/create-tables! datasource)
        (binding [*datasource* datasource]
          (f))
        (finally
          (repo/drop-tables! datasource))))))

(deftest jdbc-repository-test
  (testing "save and find account"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account (account/create-account "Mr. Black")
          saved-account (repo/save-account repository account)]

      ;; Assert the saved account 
      (is (account/valid-account? saved-account))
      (is (= "Mr. Black" (:name saved-account)))
      (is (= 0 (:balance saved-account)))
      (is (pos? (:account-number saved-account)))

      ;; Find the saved account
      (let [found-account (repo/find-account repository (:account-number saved-account))]
        (is (account/valid-saved-account? found-account))
        (is (= saved-account found-account)))))

  (testing "find non-existent account throws ExceptionInfo"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Account not found"
                           (repo/find-account repository 999999)))
      (is (= :account-not-found 
             (-> (try (repo/find-account repository 999999)
                      (catch clojure.lang.ExceptionInfo e (ex-data e)))
                 :error))))))

(deftest jdbc-repository-property-based-test
  (testing "created accounts are always valid on save"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)]
      (dotimes [_ 10]
        (let [account (account/create-account (account/gen-account-name))
              saved-account (repo/save-account repository account)]
          (is (account/valid-saved-account? saved-account))
          (is (= (:name account) (:name saved-account)))
          (is (= 0 (:balance saved-account))))))))

(deftest jdbc-repository-sequence-test
  (testing "account events get per-account sequence numbers starting from 1"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account1 (repo/save-account repository (account/create-account "Account 1"))
          account2 (repo/save-account repository (account/create-account "Account 2"))
          updated-accounts [{:account-update (account/deposit account1 100) :expected-sequence 1}
                            {:account-update (account/deposit account1 50) :expected-sequence 2}
                            {:account-update (account/deposit account1 25) :expected-sequence 3}
                            {:account-update (account/deposit account2 200) :expected-sequence 1}
                            {:account-update (account/deposit account2 150) :expected-sequence 2}]
          saved-events (mapv #(assoc % :saved-event
                                     (repo/save-account-event repository
                                                              (:account (:account-update %))
                                                              (:event (:account-update %))))
                             updated-accounts)]

      (doseq [{:keys [saved-event expected-sequence account-update]} saved-events]
        (is (= expected-sequence (:sequence saved-event))
            (str "Expected sequence " expected-sequence " for account " (:account account-update)))
        (is (= (:account-number (:account account-update)) (:account-number saved-event))
            (str "Event should belong to account " (:account account-update)))))))

(deftest jdbc-repository-concurrent-sequence-test
  (testing "concurrent account events get correct sequence numbers with retry logic"
    (let [pool (->connection-pool *container*)
          repository (repo/logging-jdbc-account-repository pool)
          account (repo/save-account repository (account/create-account "Concurrent Test Account"))
          num-events 10
          saved-event-futures (doall
                               (repeatedly num-events
                                           #(future
                                              (let [{updated-account :account event :event} (account/deposit account 1)]
                                                (repo/save-account-event repository updated-account event)))))
          saved-events (mapv deref saved-event-futures)
          sequences (mapv :sequence saved-events)]

      (try
        (is (= (set (map inc (range num-events))) (set sequences))
            (str "Expected sequences 1-10, got: " (sort sequences)))
        (is (every? #(= (:account-number account) (:account-number %)) saved-events))
        (is (every? account/valid-saved-account-event? saved-events))
        (finally
          (.close pool))))))

(deftest find-account-events-integration-test
  (testing "find-account-events returns events in reverse chronological order"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account (repo/save-account repository (account/create-account "Events Test User"))
          account-number (:account-number account)
          deposit-update (account/deposit account 100)
          withdraw-update (account/withdraw (:account deposit-update) 25)
          deposit2-update (account/deposit (:account withdraw-update) 50)]
      
      ;; Save events
      (repo/save-account-event repository (:account deposit-update) (:event deposit-update))
      (repo/save-account-event repository (:account withdraw-update) (:event withdraw-update))
      (repo/save-account-event repository (:account deposit2-update) (:event deposit2-update))
      
      ;; Retrieve events
      (let [events (repo/find-account-events repository account-number)]
        (is (= 3 (count events)))
        
        ;; Check reverse chronological order (latest first)
        (let [sequences (map :sequence events)]
          (is (= [3 2 1] sequences) "Events should be in reverse chronological order"))
        
        ;; Check event data
        (is (every? account/valid-saved-account-event? events))

        ;; Verify first event is the most recent (second deposit)
        (is (= "deposit" (:description (first events))))
        (is (= 50 (:credit (first events))))
        (is (nil? (:debit (first events)))))))

  (testing "find-account-events for non-existent account returns empty list"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          events (repo/find-account-events repository 999999)]
      (is (empty? events))))

  (testing "find-account-events for account with no events returns empty list"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account (repo/save-account repository (account/create-account "No Events User"))
          events (repo/find-account-events repository (:account-number account))]
      (is (empty? events))))

  (testing "find-account-events isolates events between different accounts"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account1 (repo/save-account repository (account/create-account "User 1"))
          account2 (repo/save-account repository (account/create-account "User 2"))]
      
      ;; Add events to each account
      (let [deposit1 (account/deposit account1 100)
            deposit2 (account/deposit account2 200)]
        (repo/save-account-event repository (:account deposit1) (:event deposit1))
        (repo/save-account-event repository (:account deposit2) (:event deposit2)))
      
      ;; Verify separate event logs
      (let [events1 (repo/find-account-events repository (:account-number account1))
            events2 (repo/find-account-events repository (:account-number account2))]
        
        (is (= 1 (count events1)))
        (is (= 1 (count events2)))
        
        ;; Verify events are account-specific
        (is (= 100 (:credit (first events1))))
        (is (= 200 (:credit (first events2))))
        (is (= "deposit" (:description (first events1))))
        (is (= "deposit" (:description (first events2))))
        
        ;; Verify sequence numbers are independent
        (is (= 1 (:sequence (first events1))))
        (is (= 1 (:sequence (first events2))))))))