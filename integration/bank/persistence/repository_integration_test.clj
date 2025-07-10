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