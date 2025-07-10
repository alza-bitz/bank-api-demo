(ns bank.domain.account-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [bank.domain.account :as account]))

(deftest create-account-test
  (testing "creates account with valid data"
    (let [result (account/create-account "Mr. Black")]
      (is (account/valid-account? result)) 
      (is (= "Mr. Black" (:name result)))
      (is (= 0 (:balance result))))) 

  (testing "validates name"
    (is (thrown? AssertionError
                 (account/create-account "")))))

(deftest create-account-event-test
  (testing "creates account event with valid data"
    (let [event (account/create-account-event 1 "deposit")]
      (is (= 1 (:account-number event)))
      (is (= "deposit" (:description event)))
      (is (inst? (:timestamp event)))))

  (testing "validates account number"
    (is (thrown? AssertionError
                 (account/create-account-event 0 "deposit"))))

  (testing "validates description"
    (is (thrown? AssertionError
                 (account/create-account-event 1 "")))))

(deftest validation-test
  (testing "valid-account? returns true for valid account"
    (let [account {:id (random-uuid) :account-number 1 :name "Test" :balance 100}]
      (is (account/valid-account? account))))

  (testing "valid-account? returns false for invalid account"
    (is (not (account/valid-account? {:id nil :account-number 1 :name "Test" :balance 100})))
    (is (not (account/valid-account? {:id (random-uuid) :account-number -1 :name "Test" :balance 100})))
    (is (not (account/valid-account? {:id (random-uuid) :account-number 1 :name "" :balance 100})))
    (is (not (account/valid-account? {:id (random-uuid) :account-number 1 :name "Test" :balance -1}))))

  (testing "valid-account-event? returns true for valid event"
    (let [event {:id (random-uuid)
                 :sequence 1
                 :account-number 1
                 :description "deposit"
                 :timestamp (java.time.Instant/now)}]
      (is (account/valid-account-event? event))))

  (testing "valid-account-event? returns false for invalid event"
    (is (not (account/valid-account-event?
              {:id (random-uuid)
               :sequence -1
               :account-number 1
               :description "deposit"
               :timestamp (java.time.Instant/now)})))))

(deftest generator-test
  (testing "generators produce valid data"
    (let [account (account/gen-account)
          event (account/gen-account-event)]
      (is (account/valid-account? account))
      (is (account/valid-account-event? event)))))

;; Property-based tests using generated data
(deftest property-based-test
  (testing "generated accounts are always valid"
    (doseq [_ (range 10)]
      (let [account (account/gen-account)]
        (is (account/valid-account? account)))))

  (testing "generated events are always valid"
    (doseq [_ (range 10)]
      (let [event (account/gen-account-event)]
        (is (account/valid-account-event? event))))))
