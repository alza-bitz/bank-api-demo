(ns bank.persistence.repository-test
  (:require
   [bank.domain.account :as account]
   [bank.persistence.repository :as repo]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [spy.assert :as spy-assert]
   [spy.core :as spy]))

(deftest jdbc-repository-test
  (testing "save-account calls next.jdbc functions correctly and returns a valid account"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                          (tx-fn ds)))
                  sql/insert! (spy/spy (fn [_ _ account _] 
                                        {:id (:id account)
                                         :account-number 1
                                         :name "Mr. Black"
                                         :balance 0}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (account/create-account "Mr. Black")
            saved-account (repo/save-account repo account)]
        (is (account/valid-saved-account? saved-account))
        (is (= 1 (:account-number saved-account)))
        (is (= "Mr. Black" (:name saved-account)))
        (is (= 0 (:balance saved-account)))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/insert!))))
  
  (testing "find-account calls next.jdbc functions correctly and returns a valid account"
    (with-redefs [sql/get-by-id (spy/spy (fn [_ _ id _ _]
                                          (when (= id 1)
                                            {:id (random-uuid) :account-number 1 :name "Mr. Black" :balance 100})))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (repo/find-account repo 1)]
        (is (account/valid-saved-account? account))
        (is (= 1 (:account-number account)))
        (is (= "Mr. Black" (:name account)))
        (is (= 100 (:balance account)))
        (spy-assert/called-once? sql/get-by-id))
      
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Account not found"
                             (repo/find-account repo 999)))
        (is (= :account-not-found 
               (-> (try (repo/find-account repo 999)
                        (catch clojure.lang.ExceptionInfo e (ex-data e)))
                   :error))))))
  
  (testing "save-account-event calls next.jdbc functions correctly and returns a valid account event"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                           (tx-fn ds)))
                  sql/update! (spy/spy (fn [_ _ _ _]))
                  jdbc/execute-one! (spy/spy (fn [_ _sql-and-params _]
                                              {:id (random-uuid)
                                               :sequence 1
                                               :account-number 1
                                               :description "deposit"
                                               :timestamp (java.sql.Timestamp/from (java.time.Instant/now))
                                               :credit 100}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            mock-saved-account (assoc (account/create-account "Mr. Black") :account-number 1)
            mock-event (account/create-account-event "deposit" {:credit 100})
            saved-event (repo/save-account-event repo mock-saved-account mock-event)]
        (is (account/valid-saved-account-event? saved-event))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/update!) 
        (spy-assert/called-once? jdbc/execute-one!)
        (is (= 1 (:sequence saved-event)))))))

(deftest deposit-event-with-credit-test
  (testing "save-account-event with deposit event includes credit amount"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                           (tx-fn ds)))
                  sql/update! (spy/spy (fn [_ _ balance-update account-filter]
                                        (is (= 100 (:balance balance-update)))
                                        (is (= 1 (:account_number account-filter)))))
                  jdbc/execute-one! (spy/spy (fn [_ _sql-and-params _]
                                              {:id (random-uuid)
                                               :sequence 1
                                               :account-number 1
                                               :description "deposit"
                                               :timestamp (java.sql.Timestamp/from (java.time.Instant/now))
                                               :credit 100}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (assoc (account/create-account "Deposit User") :account-number 1)
            {updated-account :account deposit-event :event} (account/deposit account 100)
            saved-event (repo/save-account-event repo updated-account deposit-event)]
        (is (= 100 (:credit saved-event)))
        (is (= "deposit" (:description saved-event)))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/update!)
        (spy-assert/called-once? jdbc/execute-one!)))))

(deftest save-account-event-retry-on-constraint-violation-test
  (testing "save-account-event retries on PostgreSQL constraint violation and eventually succeeds"
    (let [attempt (atom 0)]
      (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                             (tx-fn ds)))
                    sql/update! (spy/spy (fn [_ _ _ _]))
                    jdbc/execute-one! (spy/spy (fn [_ _sql-and-params _]
                                                (swap! attempt inc)
                                                (if (= @attempt 1)
                                                  (throw (org.postgresql.util.PSQLException. 
                                                          "simulating constraint violation"
                                                          org.postgresql.util.PSQLState/UNIQUE_VIOLATION))
                                                  {:id (random-uuid)
                                                   :sequence 1
                                                   :account-number 1
                                                   :description "deposit"
                                                   :timestamp (java.sql.Timestamp/from (java.time.Instant/now))
                                                   :credit 100})))]
        (let [repo (repo/->JdbcAccountRepository "mock-datasource")
              mock-saved-account (assoc (account/create-account "Mr. Black") :account-number 1)
              mock-event (account/create-account-event "deposit" {:credit 100})
              saved-event (repo/save-account-event repo mock-saved-account mock-event)]
          (is (account/valid-saved-account-event? saved-event))
          (is (= 1 (:sequence saved-event)))
          (is (= 2 @attempt) "Should have attempted twice")
          (spy-assert/called-n-times? jdbc/transact 2)
          (spy-assert/called-n-times? sql/update! 2)
          (spy-assert/called-n-times? jdbc/execute-one! 2))))))

(deftest find-account-events-test
  (testing "find-account-events calls next.jdbc functions correctly and returns valid account events"
    (with-redefs [sql/query (spy/spy (fn [_ sql-and-params _]
                                       (when (= (second sql-and-params) 1)
                                         [{:id (random-uuid) :sequence 2 :account-number 1 :description "withdraw" :timestamp (java.time.Instant/now) :debit 50}
                                          {:id (random-uuid) :sequence 1 :account-number 1 :description "deposit" :timestamp (java.time.Instant/now) :credit 100}])))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            events (repo/find-account-events repo 1)]
        (is (every? account/valid-saved-account-event? events))
        (is (= 2 (count events)))
        (is (= 2 (:sequence (first events))))
        (is (= "withdraw" (:description (first events))))
        (is (= 50 (:debit (first events))))
        (is (= 1 (:sequence (second events))))
        (is (= "deposit" (:description (second events))))
        (is (= 100 (:credit (second events))))
        (spy-assert/called-once? sql/query)))
    
    (testing "returns empty list for account with no events"
      (with-redefs [sql/query (spy/spy (fn [_ _ _] []))]
        (let [repo (repo/->JdbcAccountRepository "mock-datasource")
              events (repo/find-account-events repo 1)]
          (is (= [] events))
          (spy-assert/called-once? sql/query))))))

(deftest save-account-events-test
  (testing "save-account-events calls next.jdbc functions correctly for multiple accounts"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                          (tx-fn ds)))
                  sql/update! (spy/spy (fn [_ _ _ _] 1))
                  jdbc/execute-one! (spy/spy (fn [_ _ _]
                                              {:id (random-uuid)
                                               :sequence 1
                                               :account-number 1
                                               :description "test"
                                               :timestamp (java.time.Instant/now)
                                               :debit 50}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            sender-account {:id (random-uuid) :account-number 1 :name "Sender" :balance 50}
            receiver-account {:id (random-uuid) :account-number 2 :name "Receiver" :balance 150}
            sender-event (account/create-account-event "send to #2" {:debit 50})
            receiver-event (account/create-account-event "receive from #1" {:credit 50})
            account-event-pairs [{:account sender-account :event sender-event}
                                 {:account receiver-account :event receiver-event}]]
        (repo/save-account-events repo account-event-pairs)
        ;; Should call transact once (all in same transaction)
        (spy-assert/called-once? jdbc/transact)
        ;; Should call update! twice (once for each account)
        (spy-assert/called-n-times? sql/update! 2)
        ;; Should call execute-one! twice (once for each event)
        (spy-assert/called-n-times? jdbc/execute-one! 2)))))