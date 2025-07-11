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
    (let [event (account/create-account-event "deposit" {:credit 100})] 
      (is (= "deposit" (:description event)))
      (is (= {:credit 100} (:action event)))
      (is (inst? (:timestamp event))))) 

  (testing "validates description"
    (is (thrown? AssertionError
                 (account/create-account-event "" {:credit 100}))))

  (testing "validates action with debit or credit"
    (is (thrown? AssertionError
                 (account/create-account-event "deposit" {:debit 100 :credit 50})))
    (is (thrown? AssertionError
                 (account/create-account-event "deposit" {})))))

(deftest validation-test
  (testing "valid-account? returns true for valid account"
    (let [account {:id (random-uuid) :account-number 1 :name "Test" :balance 100}]
      (is (account/valid-account? account))))

  (testing "valid-account? returns false for invalid account"
    (is (not (account/valid-account? {:id nil :name "Test" :balance 100})))
    (is (not (account/valid-account? {:id (random-uuid) :name "" :balance 100})))
    (is (not (account/valid-account? {:id (random-uuid) :name "Test" :balance -1}))))

  (testing "valid-account-event? returns true for valid event"
    (let [event {:id (random-uuid)
                 :description "deposit"
                 :timestamp (java.time.Instant/now)
                 :action {:credit 100}}]
      (is (account/valid-account-event? event))))

  (testing "valid-account-event? returns false for invalid event"
    (is (not (account/valid-account-event?
              {:id (random-uuid)
               :description ""
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

(deftest deposit-test
  (testing "deposit increases account balance"
    (let [account (account/create-account "Deposit Test User")
          [updated-account deposit-event] (account/deposit account 100)]
      (is (= 100 (:balance updated-account)))
      (is (= "deposit" (:description deposit-event)))
      (is (= {:credit 100} (:action deposit-event)))
      (is (some? (:id deposit-event)))
      (is (inst? (:timestamp deposit-event)))))

  (testing "deposit validates positive amount"
    (let [account (account/create-account "Test User")]
      (is (thrown? AssertionError
                   (account/deposit account 0)))
      (is (thrown? AssertionError
                   (account/deposit account -50)))))

  (testing "multiple deposits accumulate balance"
    (let [account (account/create-account "Multi Deposit User")
          [account-after-first _] (account/deposit account 75)
          [account-after-second _] (account/deposit account-after-first 125)]
      (is (= 75 (:balance account-after-first)))
      (is (= 200 (:balance account-after-second))))))
