(ns bank.application.service-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [bank.persistence.repository :as repo]
            [bank.application.service :as service]
            [bank.domain.account :as account]))

(def ^:dynamic *container* nil)

(use-fixtures :once
  (fn [f]
    (let [container (-> {:image-name "postgres:13"
                         :exposed-ports [5432]
                         :env-vars {"POSTGRES_DB" "testdb"
                                    "POSTGRES_USER" "testuser"
                                    "POSTGRES_PASSWORD" "testpass"}
                         :wait-for {:wait-strategy :port}}
                        tc/create
                        tc/start!)]
      (try
        (binding [*container* container]
          (f))
        (finally
          (tc/stop! container))))))

(defn ->datasource [container]
  {:dbtype "postgresql"
   :host "localhost"
   :port (get (:mapped-ports container) 5432)
   :dbname "testdb"
   :user "testuser"
   :password "testpass"})

(def ^:dynamic *repository* nil)

(use-fixtures :each
  (fn [f]
    (let [datasource (->datasource *container*)
          repository (repo/logging-jdbc-account-repository datasource)]
      (try
        (repo/create-tables! datasource)
        (binding [*repository* repository]
          (f))
        (finally
          (repo/drop-tables! datasource))))))

(deftest account-service-integration-test
  (testing "create account end-to-end"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Mr. Black")]
      (is (account/valid-saved-account? created-account))
      (is (= "Mr. Black" (:name created-account)))
      (is (= 0 (:balance created-account)))
      (is (pos? (:account-number created-account)))))

  (testing "retrieve account end-to-end"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Mr. White")
          retrieved-account (service/retrieve-account service (:account-number created-account))]
      (is (= created-account retrieved-account))))

  (testing "deposit to account end-to-end"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Mr. Green")
          account-number (:account-number created-account)
          updated-account (service/deposit-to-account service account-number 100)]
      (is (account/valid-saved-account? updated-account))
      (is (= "Mr. Green" (:name updated-account)))
      (is (= 100 (:balance updated-account)))
      (is (= account-number (:account-number updated-account)))
      
      ;; Verify the account was updated in the database
      (let [retrieved-account (service/retrieve-account service account-number)]
        (is (= 100 (:balance retrieved-account))))))

  (testing "deposit to non-existent account throws exception"
    (let [service (service/->SyncAccountService *repository*)] 
      (is (thrown-with-msg? 
           clojure.lang.ExceptionInfo 
           #"Account not found"
           (service/deposit-to-account service 999999 100)))))

  (testing "deposit negative amount fails"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Mr. Blue")]
      (is (thrown? AssertionError
                   (service/deposit-to-account service (:account-number created-account) -50)))))

  (testing "multiple deposits accumulate correctly"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Mr. Yellow")
          account-number (:account-number created-account)]
      (service/deposit-to-account service account-number 50)
      (service/deposit-to-account service account-number 30)
      (let [final-account (service/retrieve-account service account-number)]
        (is (= 80 (:balance final-account)))))))

(deftest account-audit-service-integration-test
  (testing "retrieve account audit end-to-end"
    (let [service (service/->SyncAccountService *repository*)
          created-account (service/create-account service "Audit Test User")
          account-number (:account-number created-account)]
      
      ;; Initially, audit log should be empty
      (let [initial-events (service/retrieve-account-audit service account-number)]
        (is (empty? initial-events)))
      
      ;; Perform some transactions
      (service/deposit-to-account service account-number 100)
      (service/deposit-to-account service account-number 50)
      (service/withdraw-from-account service account-number 25)
      
      ;; Check audit log
      (let [events (service/retrieve-account-audit service account-number)]
        (is (= 3 (count events)))
        
        ;; Should be in reverse chronological order
        (is (= 3 (:sequence (first events)))) ; withdraw event (latest)
        (is (= 2 (:sequence (second events)))) ; second deposit event
        (is (= 1 (:sequence (nth events 2)))) ; first deposit event (earliest)
        
        ;; Verify event details
        (is (= "withdraw" (:description (first events))))
        (is (= 25 (:debit (first events))))
        (is (nil? (:credit (first events))))
        
        (is (= "deposit" (:description (second events))))
        (is (= 50 (:credit (second events))))
        (is (nil? (:debit (second events))))
        
        (is (= "deposit" (:description (nth events 2))))
        (is (= 100 (:credit (nth events 2))))
        (is (nil? (:debit (nth events 2))))
        
        ;; All events should belong to the same account
        (is (every? #(= account-number (:account-number %)) events)))))

  (testing "retrieve account audit for non-existent account throws exception"
    (let [service (service/->SyncAccountService *repository*)]
      (is (thrown-with-msg? 
           clojure.lang.ExceptionInfo 
           #"Account not found"
           (service/retrieve-account-audit service 999999))))))
