(ns bank.persistence.repository-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [next.jdbc :as jdbc]
            [bank.persistence.repository :as repo]
            [bank.domain.account :as account]))

;; PostgreSQL test container configuration
(def postgres-container
  (tc/create {:image-name "postgres:13"
              :exposed-ports [5432]
              :env-vars {"POSTGRES_DB" "testdb"
                         "POSTGRES_USER" "testuser"
                         "POSTGRES_PASSWORD" "testpass"}}))

(def started-container (atom nil))

(defn start-postgres! []
  (reset! started-container (tc/start! postgres-container)))

(defn stop-postgres! []
  (when @started-container
    (tc/stop! @started-container)))

(defn get-datasource []
  (let [port (get (:mapped-ports @started-container) 5432)]
    {:dbtype "postgresql"
     :host "localhost"
     :port port
     :dbname "testdb"
     :user "testuser"
     :password "testpass"}))

(use-fixtures :once
  (fn [f]
    (start-postgres!)
    (try
      (Thread/sleep 2000)
      (f)
      (finally
        (stop-postgres!)))))

(use-fixtures :each
  (fn [f]
    (let [datasource (get-datasource)]
      (try
        (repo/create-tables! datasource)
        (f)
        (finally
          (repo/drop-tables! datasource))))))

(deftest jdbc-repository-integration-test
  (testing "create and find account with real database"
    (let [datasource (get-datasource)
          repository (repo/->JdbcAccountRepository datasource)]

      ;; Create account
      (let [created-account (repo/create-account repository "Mr. Black")]
        (is (= "Mr. Black" (:name created-account)))
        (is (= 0 (:balance created-account)))
        (is (pos? (:account-number created-account)))

        ;; Find the created account
        (let [found-account (repo/find-account repository (:account-number created-account))]
          (is (= created-account found-account))))))

  (testing "find non-existent account returns nil"
    (let [datasource (get-datasource)
          repository (repo/->JdbcAccountRepository datasource)
          result (repo/find-account repository 999999)]
      (is (nil? result)))))
