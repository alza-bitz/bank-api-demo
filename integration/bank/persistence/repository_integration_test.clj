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
  (testing "create and find account with real database"
    (let [repository (repo/->JdbcAccountRepository *datasource*)]

      ;; Create account
      (let [created-account (repo/create-account repository "Mr. Black")]
        (is (account/valid-account? created-account))
        (is (= "Mr. Black" (:name created-account)))
        (is (= 0 (:balance created-account)))
        (is (pos? (:account-number created-account)))

        ;; Find the created account
        (let [found-account (repo/find-account repository (:account-number created-account))]
          (is (account/valid-account? found-account))
          (is (= created-account found-account))))))

  (testing "find non-existent account returns nil"
    (let [repository (repo/->JdbcAccountRepository *datasource*)
          account (repo/find-account repository 999999)]
      (is (nil? account)))))

(deftest jdbc-repository-property-based-test
  (testing "created accounts are always valid"
    (let [repository (repo/->JdbcAccountRepository *datasource*)]
      (dotimes [_ 10]
        (let [name (account/gen-account-name)
              created-account (repo/create-account repository name)]
          (is (account/valid-account? created-account))
          (is (= name (:name created-account)))
          (is (= 0 (:balance created-account))))))))