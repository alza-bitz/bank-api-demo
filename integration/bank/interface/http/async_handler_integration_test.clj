(ns bank.interface.http.async-handler-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [jsonista.core :as json]
            [bank.persistence.repository :as repo]
            [bank.application.service :as service]
            [bank.interface.http.routes :as routes]
            [bank.interface.http.handlers :as handlers]))

;; Test container and services
(def ^:dynamic *container* nil)
(def ^:dynamic *datasource* nil)
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
          sync-service (service/->SyncAccountService repository)
          async-service (service/consumer-pool-async-account-service repository 5)
          handler (routes/create-handler (handlers/make-handlers sync-service async-service))]
      (try
        (binding [*container* container
                  *datasource* datasource
                  *sync-service* sync-service
                  *async-service* async-service
                  *handler* handler]
          (f))
        (finally
          (service/stop async-service)
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
          submit-body (try 
                        (if (instance? java.io.ByteArrayInputStream (:body submit-response))
                          (let [body-str (slurp (:body submit-response))]
                            (json/read-value body-str))
                          (if (string? (:body submit-response))
                            (json/read-value (:body submit-response))
                            (:body submit-response)))
                        (catch Exception e
                          (println "Failed to parse submit response body:" e)
                          {}))
          operation-id (get submit-body "operation-id")
          
          ;; Wait for async operation to complete
          _ (Thread/sleep 2000)
          
          ;; Get the operation result
          result-request {:request-method :get
                          :uri (str "/operation/" operation-id)}
          result-response (*handler* result-request)
          result-body (try
                        (if (instance? java.io.ByteArrayInputStream (:body result-response))
                          (let [body-str (slurp (:body result-response))]
                            (json/read-value body-str))
                          (if (string? (:body result-response))
                            (json/read-value (:body result-response))
                            (:body result-response)))
                        (catch Exception e
                          (println "Failed to parse result response body:" e)
                          {}))
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
