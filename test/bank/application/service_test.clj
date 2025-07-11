(ns bank.application.service-test
  (:require [clojure.test :refer [deftest testing is]]
            [bank.application.service :as service]
            [bank.persistence.repository :as repo]
            [spy.core :as spy]))

(defprotocol MockRepository
  (find-account-mock [this account-number])
  (save-account-mock [this account])
  (save-account-event-mock [this account event]))

(defrecord TestMockRepository []
  repo/AccountRepository
  (find-account [this account-number]
    (find-account-mock this account-number))
  (save-account [this account]
    (save-account-mock this account))
  (save-account-event [this account event]
    (save-account-event-mock this account event)))

(deftest deposit-to-account-test
  (testing "deposits to existing account"
    (let [existing-account {:id (random-uuid)
                            :account-number 123
                            :name "John Doe"
                            :balance 50}
          mock-repo (->TestMockRepository)
          app-service (service/->DefaultAccountService mock-repo)]
      
      (with-redefs [find-account-mock (spy/spy (constantly existing-account))
                    save-account-event-mock (spy/spy (fn [_this account _event] account))]
        
        (let [result (service/deposit-to-account app-service 123 100)]
          (is (= 150 (:balance result)))
          (is (spy/called-once? find-account-mock))
          (is (spy/called-with? find-account-mock mock-repo 123))
          (is (spy/called-once? save-account-event-mock))))))

  (testing "throws exception for non-existent account"
    (let [mock-repo (->TestMockRepository)
          app-service (service/->DefaultAccountService mock-repo)]
      
      (with-redefs [find-account-mock (spy/spy (constantly nil))]
        
        (is (thrown-with-msg? 
             clojure.lang.ExceptionInfo
             #"Account not found"
             (service/deposit-to-account app-service 999 100)))
        (is (spy/called-once? find-account-mock))))))

(deftest create-account-test
  (testing "creates account through service"
    (let [created-account {:id (random-uuid)
                           :account-number 1
                           :name "Jane Doe"
                           :balance 0}
          mock-repo (->TestMockRepository)
          app-service (service/->DefaultAccountService mock-repo)]
      
      (with-redefs [save-account-mock (spy/spy (constantly created-account))]
        
        (let [result (service/create-account app-service "Jane Doe")]
          (is (= created-account result))
          (is (spy/called-once? save-account-mock)))))))

(deftest get-account-test
  (testing "retrieves account through service"
    (let [existing-account {:id (random-uuid)
                            :account-number 456
                            :name "Bob Smith"
                            :balance 200}
          mock-repo (->TestMockRepository)
          app-service (service/->DefaultAccountService mock-repo)]
      
      (with-redefs [find-account-mock (spy/spy (constantly existing-account))]
        
        (let [result (service/get-account app-service 456)]
          (is (= existing-account result))
          (is (spy/called-once? find-account-mock))
          (is (spy/called-with? find-account-mock mock-repo 456)))))))
