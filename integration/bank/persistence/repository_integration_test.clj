(ns bank.persistence.repository-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [bank.persistence.repository :as repo]
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

(def ^:dynamic *datasource* nil)

(use-fixtures :each
  (fn [f]
    (let [datasource (->datasource *container*)]
      (try
        (repo/create-tables! datasource)
        (binding [*datasource* datasource]
          (f))
        (finally
          (repo/drop-tables! datasource))))))

(deftest jdbc-repository-test
  (testing "save and find account"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account (account/create-account "Mr. Black")
          saved-account (repo/save-account repository account)]

      ;; Assert the saved account 
      (is (account/valid-account? saved-account))
      (is (= "Mr. Black" (:name saved-account)))
      (is (= 0 (:balance saved-account)))
      (is (pos? (:account-number saved-account)))

      ;; Find the saved account
      (let [found-account (repo/find-account repository (:account-number saved-account))]
        (is (account/valid-saved-account? found-account))
        (is (= saved-account found-account)))))

  (testing "find non-existent account returns nil"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          account (repo/find-account repository 999999)]
      (is (nil? account)))))

(deftest jdbc-repository-property-based-test
  (testing "created accounts are always valid on save"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)]
      (dotimes [_ 10]
        (let [account (account/create-account (account/gen-account-name))
              saved-account (repo/save-account repository account)]
          (is (account/valid-saved-account? saved-account))
          (is (= (:name account) (:name saved-account)))
          (is (= 0 (:balance saved-account))))))))

(deftest account-event-sequence-number-test
  (testing "account events get per-account sequence numbers starting from 1"
    (let [repository (repo/logging-jdbc-account-repository *datasource*)
          ;; Create two different accounts
          account1 (repo/save-account repository (account/create-account "Account 1"))
          account2 (repo/save-account repository (account/create-account "Account 2"))]
      
      ;; Create events for account1
      (let [[updated-account1-1 event1-1] (account/deposit account1 100)]
        (let [saved-event1-1 (repo/save-account-event repository updated-account1-1 event1-1)]
          (is (= 1 (:sequence saved-event1-1)))
          (is (= (:account-number account1) (:account-number saved-event1-1)))))
      
      ;; Create events for account2 (should also start at 1)
      (let [[updated-account2-1 event2-1] (account/deposit account2 200)]
        (let [saved-event2-1 (repo/save-account-event repository updated-account2-1 event2-1)]
          (is (= 1 (:sequence saved-event2-1)))
          (is (= (:account-number account2) (:account-number saved-event2-1)))))
      
      ;; Create second event for account1 (should be sequence 2)
      (let [updated-account1-2 (assoc account1 :balance 200)
            [final-account1 event1-2] (account/deposit updated-account1-2 50)]
        (let [saved-event1-2 (repo/save-account-event repository final-account1 event1-2)]
          (is (= 2 (:sequence saved-event1-2)))
          (is (= (:account-number account1) (:account-number saved-event1-2)))))
      
      ;; Create third event for account1 (should be sequence 3)
      (let [updated-account1-3 (assoc account1 :balance 250)
            [final-account1-2 event1-3] (account/deposit updated-account1-3 25)]
        (let [saved-event1-3 (repo/save-account-event repository final-account1-2 event1-3)]
          (is (= 3 (:sequence saved-event1-3)))
          (is (= (:account-number account1) (:account-number saved-event1-3)))))
      
      ;; Create second event for account2 (should be sequence 2, independent of account1)
      (let [updated-account2-2 (assoc account2 :balance 400)
            [final-account2 event2-2] (account/deposit updated-account2-2 150)]
        (let [saved-event2-2 (repo/save-account-event repository final-account2 event2-2)]
          (is (= 2 (:sequence saved-event2-2)))
          (is (= (:account-number account2) (:account-number saved-event2-2))))))))