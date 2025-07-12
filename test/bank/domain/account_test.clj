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
      (is (account/valid-account-event? event))
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
               :timestamp (java.time.Instant/now)
               :action {:credit 100}})))
    (is (not (account/valid-account-event?
              {:id (random-uuid)
               :description "deposit"
               :timestamp (java.time.Instant/now)
               :action {}})))))

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
          {:keys [account event] :as account-update} (account/deposit account 100)]
      (is (account/valid-account-update? account-update))
      (is (= 100 (:balance account)))
      (is (= "deposit" (:description event)))
      (is (= {:credit 100} (:action event)))
      (is (some? (:id event)))
      (is (inst? (:timestamp event)))))

  (testing "deposit validates positive amount"
    (let [account (account/create-account "Test User")]
      (is (thrown? AssertionError
                   (account/deposit account 0)))
      (is (thrown? AssertionError
                   (account/deposit account -50)))))

  (testing "multiple deposits accumulate balance"
    (let [account (account/create-account "Multi Deposit User")
          {first-account :account} (account/deposit account 75)
          {second-account :account} (account/deposit first-account 125)]
      (is (= 75 (:balance first-account)))
      (is (= 200 (:balance second-account))))))

(deftest withdraw-test
  (testing "withdraw decreases account balance"
    (let [account (assoc (account/create-account "Withdraw Test User") :balance 150)
          {:keys [account event] :as account-update} (account/withdraw account 100)]
      (is (account/valid-account-update? account-update))
      (is (= 50 (:balance account)))
      (is (= "withdraw" (:description event)))
      (is (= {:debit 100} (:action event)))
      (is (some? (:id event)))
      (is (inst? (:timestamp event)))))

  (testing "withdraw validates positive amount"
    (let [account (assoc (account/create-account "Test User") :balance 100)]
      (is (thrown? AssertionError
                   (account/withdraw account 0)))
      (is (thrown? AssertionError
                   (account/withdraw account -50)))))

  (testing "withdraw validates sufficient balance"
    (let [account (assoc (account/create-account "Test User") :balance 50)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Insufficient funds"
                           (account/withdraw account 100)))
      (is (= :insufficient-funds 
             (-> (try (account/withdraw account 100)
                      (catch clojure.lang.ExceptionInfo e (ex-data e)))
                 :error)))))

  (testing "withdraw can reduce balance to zero"
    (let [account (assoc (account/create-account "Zero Balance User") :balance 75)
          {updated-account :account} (account/withdraw account 75)]
      (is (= 0 (:balance updated-account)))))

  (testing "multiple withdraws reduce balance correctly"
    (let [account (assoc (account/create-account "Multi Withdraw User") :balance 200)
          {first-account :account} (account/withdraw account 50)
          {second-account :account} (account/withdraw first-account 75)]
      (is (= 150 (:balance first-account)))
      (is (= 75 (:balance second-account))))))


