(ns bank.application.async-service-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [bank.persistence.repository :as repo]
            [bank.application.service :as service]
            [bank.domain.account :as account]))

(def ^:dynamic *datasource* nil)

(def ^:dynamic *repository* nil)

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
          repository (repo/logging-jdbc-account-repository datasource)]
      (try
        (binding [*datasource* datasource
                  *repository* repository]
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

(deftest async-account-service-integration-test
  (testing "async create account end-to-end"
    (let [async-service (service/consumer-pool-async-account-service *repository* 5)]
      
      (try
        ;; Call create-account protocol function directly
        (let [operation-id (service/create-account async-service "Mr. Async")]
          
          ;; Retrieve operation result
          (let [created-account (service/retrieve-operation-result async-service operation-id)]
            (is (not (nil? created-account)))
            (is (= "Mr. Async" (:name created-account)))
            (is (= 0 (:balance created-account)))
            (is (> (:account-number created-account) 0))))
        
        (finally
          (service/stop async-service)))))

  (testing "async retrieve account end-to-end"
    (let [async-service (service/consumer-pool-async-account-service *repository* 5)
          sync-service (service/->SyncAccountService *repository*)]
      
      (try
        ;; First create an account synchronously
        (let [created-account (service/create-account sync-service "Mr. Retrieve")
              account-number (:account-number created-account)]
          
          ;; Call retrieve-account protocol function directly
          (let [operation-id (service/retrieve-account async-service account-number)]
            
            ;; Retrieve operation result
            (let [retrieved-account (service/retrieve-operation-result async-service operation-id)]
              (is (not (nil? retrieved-account)))
              (is (= "Mr. Retrieve" (:name retrieved-account)))
              (is (= 0 (:balance retrieved-account)))
              (is (= account-number (:account-number retrieved-account))))))
        
        (finally (service/stop async-service)))))

  (testing "concurrent create account operations using Malli generators"
    (let [async-service (service/consumer-pool-async-account-service *repository* 10)]
      
      (try
        ;; Generate test data using Malli generator
        (let [account-names (take 20 (repeatedly account/gen-account-name))
              operation-ids (mapv (fn [name]
                                    (service/create-account async-service name))
                                  account-names)]
          
          ;; Retrieve all results
          (let [created-accounts (mapv #(service/retrieve-operation-result async-service %) operation-ids)]
            
            ;; Verify all accounts were created
            (is (= 20 (count created-accounts)))
            (is (every? #(not (nil? %)) created-accounts))
            (is (every? #(= 0 (:balance %)) created-accounts))
            (is (every? #(> (:account-number %) 0) created-accounts))
            
            ;; Verify account numbers are unique
            (let [account-numbers (map :account-number created-accounts)]
              (is (= (count account-numbers) (count (set account-numbers)))))
            
            ;; Verify names match (though order may be different due to concurrency)
            (let [created-names (map :name created-accounts)
                  expected-names (set account-names)]
              (is (= expected-names (set created-names))))))
        
        (finally (service/stop async-service)))))

  (testing "async comprehensive operations test"
    (let [async-service (service/consumer-pool-async-account-service *repository* 5)]
      
      (try
        ;; Create two accounts for transfer test using protocol functions
        (let [sender-op-id (service/create-account async-service "Async Sender")
              receiver-op-id (service/create-account async-service "Async Receiver")
              
              sender-account (service/retrieve-operation-result async-service sender-op-id)
              receiver-account (service/retrieve-operation-result async-service receiver-op-id)
              sender-number (:account-number sender-account)
              receiver-number (:account-number receiver-account)]
          
          ;; Test async deposit
          (let [deposit-op-id (service/deposit-to-account async-service sender-number 100)
                updated-sender (service/retrieve-operation-result async-service deposit-op-id)]
            (is (= 100 (:balance updated-sender)))
            (is (= "Async Sender" (:name updated-sender))))
          
          ;; Test async transfer
          (let [transfer-op-id (service/transfer-between-accounts async-service sender-number receiver-number 30)
                transfer-result (service/retrieve-operation-result async-service transfer-op-id)]
            (is (= 70 (:balance (:sender transfer-result))))
            (is (= 30 (:balance (:receiver transfer-result)))))
          
          ;; Test async withdraw  
          (let [withdraw-op-id (service/withdraw-from-account async-service sender-number 20)
                updated-sender (service/retrieve-operation-result async-service withdraw-op-id)]
            (is (= 50 (:balance updated-sender))))
          
          ;; Test async audit
          (let [audit-op-id (service/retrieve-account-audit async-service sender-number)
                audit-events (service/retrieve-operation-result async-service audit-op-id)]
            (is (= 3 (count audit-events))) ; deposit, transfer, withdraw
            (is (= "withdraw" (:description (first audit-events))))
            (is (.startsWith (:description (second audit-events)) "send to #"))  
            (is (= "deposit" (:description (nth audit-events 2))))))
        
        (finally (service/stop async-service)))))

  (testing "operation result can only be retrieved once"
    (let [async-service (service/consumer-pool-async-account-service *repository* 5)]
      
      (try
        ;; Create an account
        (let [operation-id (service/create-account async-service "Test User")]
          
          ;; First retrieval should work
          (let [result (service/retrieve-operation-result async-service operation-id)]
            (is (not (nil? result)))
            (is (= "Test User" (:name result))))
          
          ;; Second retrieval should throw operation-not-found error
          (try
            (service/retrieve-operation-result async-service operation-id)
            (is false "Expected exception to be thrown on second retrieval")
            (catch Exception e
              (let [error-data (ex-data e)]
                (is (= :operation-not-found (:error error-data)))
                (is (= operation-id (:operation-id error-data)))))))
        
        (finally (service/stop async-service))))))
