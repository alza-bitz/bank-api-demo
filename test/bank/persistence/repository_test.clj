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
      
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            account (repo/find-account repo 999)]
        (is (nil? account)))))
  
  (testing "save-account-event calls next.jdbc functions correctly and returns a valid account event"
    (with-redefs [jdbc/transact (spy/spy (fn [ds tx-fn _]
                                           (tx-fn ds)))
                  sql/update! (spy/spy (fn [_ _ _ _]))
                  sql/insert! (spy/spy (fn [_ _ data _]
                                         {:id (random-uuid)
                                          :sequence 1
                                          :account-number (:account_number data) 
                                          :description (:description data)
                                          :timestamp (:timestamp data)}))]
      (let [repo (repo/->JdbcAccountRepository "mock-datasource")
            mock-saved-account (assoc (account/create-account "Mr. Black") :account-number 1)
            event (account/create-account-event "deposit")
            saved-event (repo/save-account-event repo mock-saved-account event)]
        (is (account/valid-saved-account-event? saved-event))
        (spy-assert/called-once? jdbc/transact)
        (spy-assert/called-once? sql/update!) 
        (spy-assert/called-once? sql/insert!)
        (is (= 1 (:sequence saved-event)))))))