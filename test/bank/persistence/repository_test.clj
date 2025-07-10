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
  (testing "create-account calls next.jdbc functions correctly and returns a valid account"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                          (tx-fn ds)))
                  sql/insert! (spy/spy (fn [_ _ data _]
                                        {:account-number 1
                                         :name (:name data)
                                         :balance 0}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (repo/create-account repo "Mr. Black")]
        (is (account/valid-account? account))
        (is (= 1 (:account-number account)))
        (is (= "Mr. Black" (:name account)))
        (is (= 0 (:balance account)))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/insert!))))
  
  (testing "find-account calls next.jdbc functions correctly and returns a valid account"
    (with-redefs [sql/get-by-id (spy/spy (fn [_ _ id _ _]
                                          (when (= id 1)
                                            {:account-number 1 :name "Mr. Black" :balance 100})))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (repo/find-account repo 1)]
        (is (account/valid-account? account))
        (is (= 1 (:account-number account)))
        (is (= "Mr. Black" (:name account)))
        (is (= 100 (:balance account)))
        (spy-assert/called-once? sql/get-by-id))
      
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (repo/find-account repo 999)]
        (is (nil? account)))))
  
  (testing "save-account-event calls next.jdbc functions correctly and returns a valid account event"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                          (tx-fn ds)))
                  sql/update! (spy/spy (fn [_ _ _ _]
                                        1)) ; return number of rows updated
                  sql/insert! (spy/spy (fn [_ _ data _]
                                        {:event-id 1
                                         :account-number (:account_number data) 
                                         :description (:description data)
                                         :timestamp (java.time.Instant/now)}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account {:account-number 1 :name "Mr. Black" :balance 150}
            event {:account-number 1 :description "deposit"}
            saved-event (repo/save-account-event repo account event)]
        (is (account/valid-account-event? saved-event))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/update!)
        (spy-assert/called-once? sql/insert!)
        (is (= 1 (:event-id saved-event)))))))