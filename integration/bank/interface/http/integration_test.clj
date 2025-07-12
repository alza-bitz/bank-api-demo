(ns bank.interface.http.integration-test
  (:require
   [bank.application.service :as service]
   [bank.interface.http.handlers :as handlers]
   [bank.interface.http.routes :as routes]
   [bank.persistence.repository :as repo]
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [jsonista.core :as json]))

(def ^:dynamic *datasource* nil)

(def ^:dynamic *service* nil)

(def ^:dynamic *handler* nil)

(defn ->datasource [container]
  {:dbtype "postgresql"
   :host "localhost"
   :port (get (:mapped-ports container) 5432)
   :dbname "testdb"
   :user "testuser"
   :password "testpass"})

(use-fixtures :once
  (fn [f]
    (let [container (-> {:image-name "postgres:13"
                         :exposed-ports [5432]
                         :env-vars {"POSTGRES_DB" "testdb"
                                    "POSTGRES_USER" "testuser"
                                    "POSTGRES_PASSWORD" "testpass"}
                         :wait-for {:wait-strategy :port}}
                        tc/create
                        tc/start!)
          datasource (->datasource container)
          repository (repo/logging-jdbc-account-repository datasource)
          service (service/->SyncAccountService repository)
          handler (routes/create-handler (handlers/make-handlers service))]
      (try
        (binding [*datasource* datasource
                  *service* service
                  *handler* handler]
          (f))
        (finally
          (tc/stop! container))))))

(use-fixtures :each
  (fn [f] 
    (try
      (repo/create-tables! *datasource*)
      (f)
      (finally
        (repo/drop-tables! *datasource*)))))

(deftest create-account-integration-test
  (testing "end-to-end account creation"
    (let [request {:request-method :post
                   :uri "/account"
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:name "John Doe"})}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "John Doe" (get body "name")))
        (is (= 0 (get body "balance")))
        (is (number? (get body "account-number")))))))

(deftest view-account-integration-test
  (testing "end-to-end account viewing"
    (let [;; First create an account
          created-account (service/create-account *service* "Jane Smith")
          account-number (:account-number created-account)

          ;; Then view it via HTTP
          request {:request-method :get
                   :uri (str "/account/" account-number)}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "Jane Smith" (get body "name")))
        (is (= 0 (get body "balance")))
        (is (= account-number (get body "account-number")))))))

(deftest deposit-integration-test
  (testing "end-to-end deposit"
    (let [;; First create an account
          created-account (service/create-account *service* "Bob Wilson")
          account-number (:account-number created-account)

          ;; Then deposit money via HTTP
          request {:request-method :post
                   :uri (str "/account/" account-number "/deposit")
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 250})}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "Bob Wilson" (get body "name")))
        (is (= 250 (get body "balance")))
        (is (= account-number (get body "account-number")))))))

(deftest account-not-found-integration-test
  (testing "404 error for non-existent account"
    (let [request {:request-method :get
                   :uri "/account/999999"}
          response (*handler* request)]

      (is (= 404 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "account-not-found" (get body "error")))
        (is (= "Account not found" (get body "message")))))))

(deftest invalid-deposit-integration-test
  (testing "400 error for invalid deposit amount"
    (let [;; First create an account
          created-account (service/create-account *service* "Test User")
          account-number (:account-number created-account)

          ;; Try to deposit invalid amount
          request {:request-method :post
                   :uri (str "/account/" account-number "/deposit")
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 0})}
          response (*handler* request)]

      (is (= 400 (:status response)))
      (let [body (json/read-value (:body response))]
        ;; Reitit coercion middleware returns validation errors in this format
        (is (= "reitit.coercion/request-coercion" (get body "type")))
        (is (= "malli" (get body "coercion")))
        (is (contains? body "humanized"))
        (is (contains? (get body "humanized") "amount"))
        (is (= ["should be at least 1"] (get-in body ["humanized" "amount"])))))))

(deftest withdraw-integration-test
  (testing "end-to-end withdraw with sufficient funds"
    (let [;; First create an account and deposit money
          created-account (service/create-account *service* "Alice Brown")
          account-number (:account-number created-account)
          _ (service/deposit-to-account *service* account-number 500)

          ;; Then withdraw money via HTTP
          request {:request-method :post
                   :uri (str "/account/" account-number "/withdraw")
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 200})}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "Alice Brown" (get body "name")))
        (is (= 300 (get body "balance")))
        (is (= account-number (get body "account-number")))))))

(deftest withdraw-insufficient-funds-integration-test
  (testing "400 error for insufficient funds"
    (let [;; First create an account with small deposit
          created-account (service/create-account *service* "Poor User")
          account-number (:account-number created-account)
          _ (service/deposit-to-account *service* account-number 50)

          ;; Try to withdraw more than available
          request {:request-method :post
                   :uri (str "/account/" account-number "/withdraw")
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 100})}
          response (*handler* request)]

      (is (= 400 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "insufficient-funds" (get body "error")))
        (is (= "Insufficient funds for withdrawal" (get body "message")))))))

(deftest withdraw-account-not-found-integration-test
  (testing "404 error for withdrawing from non-existent account"
    (let [request {:request-method :post
                   :uri "/account/999999/withdraw"
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 50})}
          response (*handler* request)]

      (is (= 404 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "account-not-found" (get body "error")))
        (is (= "Account not found" (get body "message")))))))

(deftest invalid-withdraw-integration-test
  (testing "400 error for invalid withdraw amount"
    (let [;; First create an account
          created-account (service/create-account *service* "Test User")
          account-number (:account-number created-account)

          ;; Try to withdraw invalid amount
          request {:request-method :post
                   :uri (str "/account/" account-number "/withdraw")
                   :headers {"content-type" "application/json"}
                   :body (json/write-value-as-string {:amount 0})}
          response (*handler* request)]

      (is (= 400 (:status response)))
      (let [body (json/read-value (:body response))]
        ;; Reitit coercion middleware returns validation errors in this format
        (is (= "reitit.coercion/request-coercion" (get body "type")))
        (is (= "malli" (get body "coercion")))
        (is (contains? body "humanized"))
        (is (contains? (get body "humanized") "amount"))
        (is (= ["should be at least 1"] (get-in body ["humanized" "amount"])))))))

(deftest audit-log-integration-test
  (testing "end-to-end audit log with multiple transactions"
    (let [;; Create account and perform transactions
          created-account (service/create-account *service* "Audit User")
          account-number (:account-number created-account)
          _ (service/deposit-to-account *service* account-number 1000)
          _ (service/withdraw-from-account *service* account-number 200)
          _ (service/deposit-to-account *service* account-number 300)

          ;; Retrieve audit log via HTTP
          request {:request-method :get
                   :uri (str "/account/" account-number "/audit")}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (vector? body))
        (is (= 3 (count body)))
        
        ;; Check reverse chronological order
        (let [[latest second-latest oldest] body]
          ;; Latest: deposit 300
          (is (= 3 (get latest "sequence")))
          (is (= 300 (get latest "credit")))
          (is (= "deposit" (get latest "description")))
          (is (nil? (get latest "debit")))
          
          ;; Second latest: withdraw 200
          (is (= 2 (get second-latest "sequence")))
          (is (= 200 (get second-latest "debit")))
          (is (= "withdraw" (get second-latest "description")))
          (is (nil? (get second-latest "credit")))
          
          ;; Oldest: deposit 1000
          (is (= 1 (get oldest "sequence")))
          (is (= 1000 (get oldest "credit")))
          (is (= "deposit" (get oldest "description")))
          (is (nil? (get oldest "debit"))))))))

(deftest audit-log-empty-integration-test
  (testing "empty audit log for account with no transactions"
    (let [;; Create account with no transactions
          created-account (service/create-account *service* "No Transactions User")
          account-number (:account-number created-account)

          ;; Retrieve audit log via HTTP
          request {:request-method :get
                   :uri (str "/account/" account-number "/audit")}
          response (*handler* request)]

      (is (= 200 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (vector? body))
        (is (= 0 (count body)))))))

(deftest audit-log-account-not-found-integration-test
  (testing "404 error for audit log of non-existent account"
    (let [request {:request-method :get
                   :uri "/account/999999/audit"}
          response (*handler* request)]

      (is (= 404 (:status response)))
      (let [body (json/read-value (:body response))]
        (is (= "account-not-found" (get body "error")))
        (is (= "Account not found" (get body "message")))))))
