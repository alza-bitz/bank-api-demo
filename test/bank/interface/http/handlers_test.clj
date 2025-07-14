(ns bank.interface.http.handlers-test
  (:require [clojure.test :refer [deftest is testing]]
            [bank.interface.http.handlers :as handlers]
            [bank.application.service :as service]))

(def mock-service
  "Mock service for testing handlers."
  (reify service/AccountService
    (create-account [_ name]
      {:id (random-uuid)
       :account-number 123
       :name name
       :balance 0})
    (retrieve-account [_ account-number]
      (if (= account-number 123)
        {:id (random-uuid)
         :account-number 123
         :name "Test User"
         :balance 100}
        (throw (ex-info "Account not found" {:error :account-not-found :account-number account-number}))))
    (deposit-to-account [_ account-number amount]
      (if (= account-number 123)
        {:id (random-uuid)
         :account-number 123
         :name "Test User"
         :balance (+ 100 amount)}
        (throw (ex-info "Account not found" {:error :account-not-found :account-number account-number}))))
    (withdraw-from-account [_ account-number amount]
      (cond
        (not= account-number 123)
        (throw (ex-info "Account not found" {:error :account-not-found :account-number account-number}))
        
        (> amount 100)
        (throw (ex-info "Insufficient funds" {:error :insufficient-funds :account-number account-number :balance 100 :amount amount}))
        
        :else
        {:id (random-uuid)
         :account-number 123
         :name "Test User"
         :balance (- 100 amount)}))
    (retrieve-account-audit [_ account-number]
      (if (= account-number 123)
        [{:sequence 2
          :description "withdraw"
          :debit 50
          :credit nil}
         {:sequence 1
          :description "deposit"
          :debit nil
          :credit 100}]
        (throw (ex-info "Account not found" {:error :account-not-found :account-number account-number}))))))

(deftest create-account-handler-test
  (testing "successful account creation"
    (let [handler (handlers/create-account-handler mock-service)
          request {:body-params {:name "John Doe"}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= 123 (get-in response [:body :account-number])))
      (is (= "John Doe" (get-in response [:body :name])))
      (is (= 0 (get-in response [:body :balance])))))

  (testing "service exception handling"
    (let [failing-service (reify service/AccountService
                           (create-account [_ _]
                             (throw (RuntimeException. "Database error")))
                           (retrieve-account [_ _] nil)
                           (deposit-to-account [_ _ _] nil)
                           (withdraw-from-account [_ _ _] nil)
                           (transfer-between-accounts [_ _ _ _] nil)
                           (retrieve-account-audit [_ _] nil))
          handler (handlers/create-account-handler failing-service)
          request {:body-params {:name "John Doe"}}
          response (handler request)]
      (is (= 500 (:status response)))
      (is (= "internal-server-error" (get-in response [:body :error])))
      (is (= "Failed to create account" (get-in response [:body :message]))))))

(deftest view-account-handler-test
  (testing "successful account retrieval"
    (let [handler (handlers/view-account-handler mock-service)
          request {:path-params {:id "123"}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= 123 (get-in response [:body :account-number])))
      (is (= "Test User" (get-in response [:body :name])))
      (is (= 100 (get-in response [:body :balance])))))

  (testing "invalid account number format"
    (let [handler (handlers/view-account-handler mock-service)
          request {:path-params {:id "abc"}}
          response (handler request)]
      (is (= 400 (:status response)))
      (is (= "bad-request" (get-in response [:body :error])))
      (is (= "Invalid account number format" (get-in response [:body :message])))))

  (testing "account not found"
    (let [handler (handlers/view-account-handler mock-service)
          request {:path-params {:id "999"}}
          response (handler request)]
      (is (= 404 (:status response)))
      (is (= "account-not-found" (get-in response [:body :error])))
      (is (= "Account not found" (get-in response [:body :message])))))

  (testing "service exception handling"
    (let [failing-service (reify service/AccountService
                           (create-account [_ _] nil)
                           (retrieve-account [_ _]
                             (throw (RuntimeException. "Database error")))
                           (deposit-to-account [_ _ _] nil)
                           (withdraw-from-account [_ _ _] nil)
                           (transfer-between-accounts [_ _ _ _] nil)
                           (retrieve-account-audit [_ _] nil))
          handler (handlers/view-account-handler failing-service)
          request {:path-params {:id "123"}}
          response (handler request)]
      (is (= 500 (:status response)))
      (is (= "internal-server-error" (get-in response [:body :error])))
      (is (= "Failed to retrieve account" (get-in response [:body :message]))))))

(deftest deposit-handler-test
  (testing "successful deposit"
    (let [handler (handlers/deposit-handler mock-service)
          request {:path-params {:id "123"}
                  :body-params {:amount 50}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= 123 (get-in response [:body :account-number])))
      (is (= "Test User" (get-in response [:body :name])))
      (is (= 150 (get-in response [:body :balance])))))

  (testing "invalid account number format"
    (let [handler (handlers/deposit-handler mock-service)
          request {:path-params {:id "abc"}
                  :body-params {:amount 50}}
          response (handler request)]
      (is (= 400 (:status response)))
      (is (= "bad-request" (get-in response [:body :error])))
      (is (= "Invalid account number format" (get-in response [:body :message])))))

  (testing "account not found"
    (let [handler (handlers/deposit-handler mock-service)
          request {:path-params {:id "999"}
                  :body-params {:amount 50}}
          response (handler request)]
      (is (= 404 (:status response)))
      (is (= "account-not-found" (get-in response [:body :error])))
      (is (= "Account not found" (get-in response [:body :message])))))

  (testing "service exception handling"
    (let [failing-service (reify service/AccountService
                           (create-account [_ _] nil)
                           (retrieve-account [_ _] nil)
                           (deposit-to-account [_ _ _]
                             (throw (RuntimeException. "Database error")))
                           (withdraw-from-account [_ _ _] nil)
                           (transfer-between-accounts [_ _ _ _] nil)
                           (retrieve-account-audit [_ _] nil))
          handler (handlers/deposit-handler failing-service)
          request {:path-params {:id "123"}
                  :body-params {:amount 50}}
          response (handler request)]
      (is (= 500 (:status response)))
      (is (= "internal-server-error" (get-in response [:body :error])))
      (is (= "Failed to deposit to account" (get-in response [:body :message]))))))

(deftest withdraw-handler-test
  (testing "successful withdrawal"
    (let [handler (handlers/withdraw-handler mock-service)
          request {:path-params {:id "123"}
                   :body-params {:amount 50}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= 123 (get-in response [:body :account-number])))
      (is (= "Test User" (get-in response [:body :name])))
      (is (= 50 (get-in response [:body :balance])))))

  (testing "invalid account number format"
    (let [handler (handlers/withdraw-handler mock-service)
          request {:path-params {:id "abc"}
                   :body-params {:amount 50}}
          response (handler request)]
      (is (= 400 (:status response)))
      (is (= "bad-request" (get-in response [:body :error])))
      (is (= "Invalid account number format" (get-in response [:body :message])))))

  (testing "insufficient funds"
    (let [handler (handlers/withdraw-handler mock-service)
          request {:path-params {:id "123"}
                   :body-params {:amount 150}}
          response (handler request)]
      (is (= 422 (:status response)))
      (is (= "insufficient-funds" (get-in response [:body :error])))
      (is (= "Insufficient funds for withdrawal" (get-in response [:body :message])))))

  (testing "account not found"
    (let [handler (handlers/withdraw-handler mock-service)
          request {:path-params {:id "999"}
                   :body-params {:amount 50}}
          response (handler request)]
      (is (= 404 (:status response)))
      (is (= "account-not-found" (get-in response [:body :error])))
      (is (= "Account not found" (get-in response [:body :message])))))

  (testing "service exception handling"
    (let [failing-service (reify service/AccountService
                           (create-account [_ _] nil)
                           (retrieve-account [_ _] nil)
                           (deposit-to-account [_ _ _] nil)
                           (withdraw-from-account [_ _ _]
                             (throw (RuntimeException. "Database error")))
                           (transfer-between-accounts [_ _ _ _] nil)
                           (retrieve-account-audit [_ _] nil))
          handler (handlers/withdraw-handler failing-service)
          request {:path-params {:id "123"}
                   :body-params {:amount 50}}
          response (handler request)]
      (is (= 500 (:status response)))
      (is (= "internal-server-error" (get-in response [:body :error])))
      (is (= "Failed to withdraw from account" (get-in response [:body :message]))))))

(deftest make-handlers-test
  (testing "make-handlers returns all expected handlers"
    (let [handlers (handlers/make-handlers mock-service)]
      (is (fn? (:create-account handlers)))
      (is (fn? (:view-account handlers)))
      (is (fn? (:deposit handlers)))
      (is (fn? (:withdraw handlers)))
      (is (fn? (:audit handlers))))))

(deftest audit-handler-test
  (testing "successful audit retrieval"
    (let [handler (handlers/audit-handler mock-service)
          request {:path-params {:id "123"}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= 2 (count (:body response))))
      (is (= 2 (get-in response [:body 0 :sequence])))
      (is (= "withdraw" (get-in response [:body 0 :description])))
      (is (= 50 (get-in response [:body 0 :debit])))
      (is (= 1 (get-in response [:body 1 :sequence])))
      (is (= "deposit" (get-in response [:body 1 :description])))
      (is (= 100 (get-in response [:body 1 :credit])))))

  (testing "account not found"
    (let [handler (handlers/audit-handler mock-service)
          request {:path-params {:id "999"}}
          response (handler request)]
      (is (= 404 (:status response)))
      (is (= "account-not-found" (get-in response [:body :error])))
      (is (= "Account not found" (get-in response [:body :message])))))

  (testing "service exception handling"
    (let [failing-service (reify service/AccountService
                           (create-account [_ _] nil)
                           (retrieve-account [_ _] nil)
                           (deposit-to-account [_ _ _] nil)
                           (withdraw-from-account [_ _ _] nil)
                           (transfer-between-accounts [_ _ _ _] nil)
                           (retrieve-account-audit [_ _]
                             (throw (RuntimeException. "Database error"))))
          handler (handlers/audit-handler failing-service)
          request {:path-params {:id "123"}}
          response (handler request)]
      (is (= 500 (:status response)))
      (is (= "internal-server-error" (get-in response [:body :error])))
      (is (= "Failed to retrieve audit log" (get-in response [:body :message]))))))
