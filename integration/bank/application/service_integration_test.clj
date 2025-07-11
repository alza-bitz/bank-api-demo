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
                                    "POSTGRES_PASSWORD" "testpass"}}
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

(def ^:dynamic *service* nil)

(use-fixtures :each
  (fn [f]
    (let [datasource (->datasource *container*)
          repository (repo/logging-jdbc-account-repository datasource)
          app-service (service/->DefaultAccountService repository)]
      (try
        (repo/create-tables! datasource)
        (binding [*service* app-service]
          (f))
        (finally
          (repo/drop-tables! datasource))))))

(deftest application-service-integration-test
  (testing "create account end-to-end"
    (let [created-account (service/create-account *service* "Mr. Black")]
      (is (account/valid-saved-account? created-account))
      (is (= "Mr. Black" (:name created-account)))
      (is (= 0 (:balance created-account)))
      (is (pos? (:account-number created-account)))))

  (testing "get account end-to-end"
    (let [created-account (service/create-account *service* "Mr. White")
          retrieved-account (service/get-account *service* (:account-number created-account))]
      (is (= created-account retrieved-account))))

  (testing "deposit to account end-to-end"
    (let [created-account (service/create-account *service* "Mr. Green")
          account-number (:account-number created-account)
          updated-account (service/deposit-to-account *service* account-number 100)]
      (is (account/valid-saved-account? updated-account))
      (is (= "Mr. Green" (:name updated-account)))
      (is (= 100 (:balance updated-account)))
      (is (= account-number (:account-number updated-account)))
      
      ;; Verify the account was updated in the database
      (let [retrieved-account (service/get-account *service* account-number)]
        (is (= 100 (:balance retrieved-account))))))

  (testing "deposit to non-existent account throws exception"
    (is (thrown-with-msg? 
         clojure.lang.ExceptionInfo 
         #"Account not found"
         (service/deposit-to-account *service* 999999 100))))

  (testing "deposit negative amount fails"
    (let [created-account (service/create-account *service* "Mr. Blue")]
      (is (thrown? AssertionError
                   (service/deposit-to-account *service* (:account-number created-account) -50)))))

  (testing "multiple deposits accumulate correctly"
    (let [created-account (service/create-account *service* "Mr. Yellow")
          account-number (:account-number created-account)]
      (service/deposit-to-account *service* account-number 50)
      (service/deposit-to-account *service* account-number 30)
      (let [final-account (service/get-account *service* account-number)]
        (is (= 80 (:balance final-account)))))))
