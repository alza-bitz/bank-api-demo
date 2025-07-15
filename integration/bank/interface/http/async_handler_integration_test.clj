(ns bank.interface.http.async-handler-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [jsonista.core :as json]
            [bank.persistence.repository :as repo]
            [bank.application.service :as service]
            [bank.interface.http.routes :as routes]
            [bank.interface.http.handlers :as handlers])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

;; Test container and services
(def ^:dynamic *datasource* nil)
(def ^:dynamic *datasource-pooled* nil)
(def ^:dynamic *sync-service* nil)
(def ^:dynamic *async-service* nil)
(def ^:dynamic *handler* nil)

(defn ->datasource [container]
  {:dbtype "postgresql"
   :host "localhost"
   :port (get (:mapped-ports container) 5432)
   :dbname "testdb"
   :user "testuser"
   :password "testpass"})

(defn ->datasource-pooled
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
          datasource-pooled (->datasource-pooled container)
          repository (repo/logging-jdbc-account-repository datasource-pooled)
          sync-service (service/->SyncAccountService repository)
          async-service (service/consumer-pool-async-account-service repository 5)
          handler (routes/create-handler (handlers/make-handlers sync-service async-service))]
      (try
        (binding [*datasource* datasource
                  *datasource-pooled* datasource-pooled
                  *sync-service* sync-service
                  *async-service* async-service
                  *handler* handler]
          (f))
        (finally
          (service/stop async-service)
          (.close datasource-pooled)
          (tc/stop! container))))))

(use-fixtures :each
  (fn [f]
    (try
      (repo/create-tables! *datasource*)
      (f)
      (finally
        (repo/drop-tables! *datasource*)))))

(deftest async-create-account-integration-test
  (testing "async create account via handler"
    (let [;; Submit async create account request 
          submit-request {:request-method :post
                          :uri "/account"
                          :query-params {"async" "true"}
                          :headers {"content-type" "application/json"}
                          :body (json/write-value-as-string {:name "Async Test User"})}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          account (get result-body "result")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= "Async Test User" (get account "name")))
      (is (= 0 (get account "balance")))
      (is (> (get account "account-number") 0)))))

(deftest async-view-account-integration-test
  (testing "async view account via handler"
    ;; Create account first
    (let [created-account (service/create-account *sync-service* "Async View Test User")
          account-number (:account-number created-account)
          
          ;; Submit async view account request
          submit-request {:request-method :get
                          :uri (str "/account/" account-number)
                          :query-params {"async" "true"}}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          account (get result-body "result")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= "Async View Test User" (get account "name")))
      (is (= 0 (get account "balance")))
      (is (= account-number (get account "account-number"))))))

(deftest async-deposit-integration-test
  (testing "async deposit via handler"
    ;; Create account first
    (let [created-account (service/create-account *sync-service* "Async Deposit Test User")
          account-number (:account-number created-account)
          
          ;; Submit async deposit request
          submit-request {:request-method :post
                          :uri (str "/account/" account-number "/deposit")
                          :query-params {"async" "true"}
                          :headers {"content-type" "application/json"}
                          :body (json/write-value-as-string {:amount 100})}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          account (get result-body "result")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= "Async Deposit Test User" (get account "name")))
      (is (= 100 (get account "balance")))
      (is (= account-number (get account "account-number"))))))

(deftest async-withdraw-integration-test
  (testing "async withdraw via handler"
    ;; Create account and deposit money first
    (let [created-account (service/create-account *sync-service* "Async Withdraw Test User")
          account-number (:account-number created-account)
          _ (service/deposit-to-account *sync-service* account-number 200)
          
          ;; Submit async withdraw request
          submit-request {:request-method :post
                          :uri (str "/account/" account-number "/withdraw")
                          :query-params {"async" "true"}
                          :headers {"content-type" "application/json"}
                          :body (json/write-value-as-string {:amount 50})}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          account (get result-body "result")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= "Async Withdraw Test User" (get account "name")))
      (is (= 150 (get account "balance")))
      (is (= account-number (get account "account-number"))))))

(deftest async-transfer-integration-test
  (testing "async transfer via handler"
    ;; Create accounts and fund sender first
    (let [sender-account (service/create-account *sync-service* "Async Sender")
          receiver-account (service/create-account *sync-service* "Async Receiver")
          sender-number (:account-number sender-account)
          receiver-number (:account-number receiver-account)
          _ (service/deposit-to-account *sync-service* sender-number 300)
          
          ;; Submit async transfer request
          submit-request {:request-method :post
                          :uri (str "/account/" sender-number "/send")
                          :query-params {"async" "true"}
                          :headers {"content-type" "application/json"}
                          :body (json/write-value-as-string {:amount 100
                                                             :account-number receiver-number})}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          transfer-result (get result-body "result")
          sender-account-result (get transfer-result "sender")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= "Async Sender" (get sender-account-result "name")))
      (is (= 200 (get sender-account-result "balance")))
      (is (= sender-number (get sender-account-result "account-number")))
      
      ;; Verify receiver account was updated
      (let [receiver-account-updated (service/retrieve-account *sync-service* receiver-number)]
        (is (= 100 (:balance receiver-account-updated)))))))

(deftest async-audit-integration-test
  (testing "async audit via handler"
    ;; Create account and perform operations first
    (let [account (service/create-account *sync-service* "Async Audit Test User")
          account-number (:account-number account)
          _ (service/deposit-to-account *sync-service* account-number 500)
          _ (service/withdraw-from-account *sync-service* account-number 100)
          
          ;; Submit async audit request
          submit-request {:request-method :get
                          :uri (str "/account/" account-number "/audit")
                          :query-params {"async" "true"}}
          submit-response (*handler* submit-request)
          submit-body (json/read-value (:body submit-response))
          operation-id (get submit-body "operation-id")
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (json/read-value (:body result-response))
          audit-log (get result-body "result")]
      
      (is (= 202 (:status submit-response)))
      (is (string? operation-id))
      
      (is (= 200 (:status result-response)))
      (is (= "completed" (get result-body "status")))
      (is (= 2 (count audit-log)))
      
      ;; Check audit log entries (reverse chronological order)
      (let [first-entry (first audit-log)
            second-entry (second audit-log)]
        (is (= 2 (get first-entry "sequence")))
        (is (= 100 (get first-entry "debit")))
        (is (= "withdraw" (get first-entry "description")))
        
        (is (= 1 (get second-entry "sequence")))
        (is (= 500 (get second-entry "credit")))
        (is (= "deposit" (get second-entry "description")))))))

(deftest concurrent-async-create-operations-test
  (testing "100 concurrent async create account operations via handler"
    (let [account-names (repeatedly 100 #(str "Concurrent User " (rand-int 1000000)))
          
          ;; Submit all operations concurrently using pmap
          operation-ids (pmap (fn [name]
                                (let [request {:request-method :post
                                               :uri "/account"
                                               :query-params {"async" "true"}
                                               :headers {"content-type" "application/json"}
                                               :body (json/write-value-as-string {:name name})}
                                      response (*handler* request)
                                      body (json/read-value (:body response))]
                                  (get body "operation-id")))
                              account-names)
          
          ;; Retrieve all results
          results (pmap (fn [operation-id]
                          (let [request {:request-method :get
                                         :uri (str "/operation/" operation-id)}
                                response (*handler* request)]
                            (json/read-value (:body response))))
                        operation-ids)]
      
      ;; Verify all operations completed successfully
      (is (= 100 (count results)))
      (is (every? #(= "completed" (get % "status")) results))
      
      ;; Verify all accounts were created with unique account numbers
      (let [accounts (map #(get % "result") results)
            account-numbers (map #(get % "account-number") accounts)]
        (is (= 100 (count account-numbers)))
        (is (= 100 (count (set account-numbers)))) ; All unique
        (is (every? #(= 0 (get % "balance")) accounts))
        (is (every? #(> (get % "account-number") 0) accounts))))))

(deftest mixed-sync-async-operations-test
  (testing "mixed sync and async operations on existing accounts via handler"
    ;; Create some accounts synchronously first
    (let [accounts (repeatedly 50 #(service/create-account *sync-service* 
                                                           (str "Mixed Test " (rand-int 1000000))))
          account-numbers (map :account-number accounts)
          
          ;; Submit async view operations for all accounts
          view-operation-ids (pmap (fn [account-number]
                                     (let [request {:request-method :get
                                                    :uri (str "/account/" account-number)
                                                    :query-params {"async" "true"}}
                                           response (*handler* request)
                                           body (json/read-value (:body response))]
                                       (get body "operation-id")))
                                   account-numbers)
          
          ;; Retrieve all view results
          view-results (pmap (fn [operation-id]
                               (let [request {:request-method :get
                                              :uri (str "/operation/" operation-id)}
                                     response (*handler* request)]
                                 (json/read-value (:body response))))
                             view-operation-ids)]
      
      ;; Verify all view operations completed successfully
      (is (= 50 (count view-results)))
      (is (every? #(= "completed" (get % "status")) view-results))
      
      ;; Verify accounts match the original created accounts
      (let [retrieved-accounts (map #(get % "result") view-results)
            retrieved-numbers (set (map #(get % "account-number") retrieved-accounts))
            original-numbers (set account-numbers)]
        (is (= original-numbers retrieved-numbers))
        (is (every? #(= 0 (get % "balance")) retrieved-accounts))))))

(deftest concurrent-async-mixed-operations-test
  (testing "concurrent async mixed operations (deposit, withdraw, transfer) via handler"
    ;; Create initial accounts for transfers
    (let [accounts (repeatedly 20 #(service/create-account *sync-service* 
                                                           (str "Concurrent Mixed " (rand-int 1000000))))
          account-numbers (map :account-number accounts)
          
          ;; Fund some accounts for withdraw and transfer operations
          _ (doseq [account-number (take 10 account-numbers)]
              (service/deposit-to-account *sync-service* account-number 1000))
          
          ;; Submit mixed async operations concurrently
          deposit-operations (pmap (fn [account-number]
                                     (let [request {:request-method :post
                                                    :uri (str "/account/" account-number "/deposit")
                                                    :query-params {"async" "true"}
                                                    :headers {"content-type" "application/json"}
                                                    :body (json/write-value-as-string {:amount (+ 50 (rand-int 100))})}
                                           response (*handler* request)
                                           body (json/read-value (:body response))]
                                       (get body "operation-id")))
                                   account-numbers)
          
          withdraw-operations (pmap (fn [account-number]
                                      (let [request {:request-method :post
                                                     :uri (str "/account/" account-number "/withdraw")
                                                     :query-params {"async" "true"}
                                                     :headers {"content-type" "application/json"}
                                                     :body (json/write-value-as-string {:amount (+ 10 (rand-int 30))})}
                                            response (*handler* request)
                                            body (json/read-value (:body response))]
                                        (get body "operation-id")))
                                    (take 10 account-numbers))
          
          transfer-operations (pmap (fn [[sender receiver]]
                                      (let [request {:request-method :post
                                                     :uri (str "/account/" sender "/send")
                                                     :query-params {"async" "true"}
                                                     :headers {"content-type" "application/json"}
                                                     :body (json/write-value-as-string {:amount (+ 20 (rand-int 50))
                                                                                        :account-number receiver})}
                                            response (*handler* request)
                                            body (json/read-value (:body response))]
                                        (get body "operation-id")))
                                    (partition 2 (take 10 account-numbers)))
          
          all-operation-ids (concat deposit-operations withdraw-operations transfer-operations)
          
          ;; Retrieve all results
          results (pmap (fn [operation-id]
                          (let [request {:request-method :get
                                         :uri (str "/operation/" operation-id)}
                                response (*handler* request)]
                            (json/read-value (:body response))))
                        all-operation-ids)]
      
      ;; Verify all operations completed successfully
      (is (= (+ 20 10 5) (count results))) ; 20 deposits + 10 withdraws + 5 transfers
      (is (every? #(= "completed" (get % "status")) results))
      
      ;; Verify no operation failed
      (is (not-any? #(= "failed" (get % "status")) results)))))
