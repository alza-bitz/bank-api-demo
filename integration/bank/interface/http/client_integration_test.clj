(ns bank.interface.http.client-integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-test-containers.core :as tc]
            [jsonista.core :as json]
            [clj-http.client :as http]
            [bank.persistence.repository :as repo]
            [bank.application.service]  ; Load service integrant methods
            [bank.interface.http.handlers]  ; Load handlers integrant methods
            [bank.interface.http.routes]    ; Load routes integrant methods  
            [bank.interface.http.server]    ; Load server integrant methods
            [integrant.core :as ig])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

;; Test container and system
(def ^:dynamic *container* nil)
(def ^:dynamic *datasource* nil)
(def ^:dynamic *system* nil)
(def ^:dynamic *base-url* nil)

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
                 (.setMaximumPoolSize 50)
                 (.setMinimumIdle 10)
                 (.setConnectionTimeout 30000))]
    (HikariDataSource. config)))

(defn create-test-system 
  "Create a minimal system configuration for testing"
  [datasource-pooled]
  {:bank.persistence.repository/repository {:datasource datasource-pooled}
   :bank.application.service/sync-service {:repository (ig/ref :bank.persistence.repository/repository)}
   :bank.application.service/async-service {:repository (ig/ref :bank.persistence.repository/repository)
                                            :consumer-pool-size 20}
   :bank.interface.http.handlers/handlers {:sync-service (ig/ref :bank.application.service/sync-service)
                                           :async-service (ig/ref :bank.application.service/async-service)}
   :bank.interface.http.routes/handler {:handlers (ig/ref :bank.interface.http.handlers/handlers)}
   :bank.interface.http.server/server {:handler (ig/ref :bank.interface.http.routes/handler)
                                       :port 0}}) ; Use port 0 to get a random available port

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
          system-config (create-test-system datasource-pooled)
          system (ig/init system-config)
          http-server (-> system :bank.interface.http.server/server)
          jetty-server (:server http-server)
          server-port (-> jetty-server .getConnectors first .getLocalPort)
          base-url (str "http://localhost:" server-port)]
      (try
        (binding [*container* container
                  *datasource* datasource
                  *system* system
                  *base-url* base-url]
          (f))
        (finally
          (ig/halt! system)
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
  (testing "async create account via HTTP client"
    (let [;; Submit async create account request
          submit-response (http/post (str *base-url* "/account")
                                     {:query-params {"async" "true"}
                                      :headers {"content-type" "application/json"}
                                      :body (json/write-value-as-string {:name "Client Test User"})
                                      :throw-exceptions false})
          submit-body-str (:body submit-response)
          submit-body (json/read-value submit-body-str)
          operation-id (get submit-body "operation-id")]
        
        ;; Verify submit response
        (is (= 202 (:status submit-response)))
        (is (string? operation-id))
        
        ;; Get the operation result
        (let [result-response (http/get (str *base-url* "/operation/" operation-id)
                                        {:throw-exceptions false})
              result-body-str (:body result-response)
              result-body (json/read-value result-body-str)
              account (get result-body "result")]
          
          ;; Verify result response
          (is (= 200 (:status result-response)))
          (is (= "completed" (get result-body "status")))
          (is (= "Client Test User" (get account "name")))
          (is (= 0 (get account "balance")))
          (is (> (get account "account-number") 0))))))

(deftest sync-create-account-integration-test
  (testing "sync create account via HTTP client"
    (let [response (http/post (str *base-url* "/account")
                              {:headers {"content-type" "application/json"}
                               :body (json/write-value-as-string {:name "Sync Client Test User"})
                               :throw-exceptions false})
          body-str (:body response)
          body (when body-str (json/read-value body-str))]
      
      ;; Verify sync response
      (is (= 200 (:status response)))
      (is (= "Sync Client Test User" (get body "name")))
      (is (= 0 (get body "balance")))
      (is (> (get body "account-number") 0))
      (is (nil? (get body "operation-id"))) ; Should not have operation-id for sync  
      (is (nil? (get body "status"))))))

(deftest async-query-param-variations-test
  (testing "different ways of specifying async=true query parameter"
    (let [test-cases [
            {:async "true" :description "string 'true'"}
            {:async true :description "boolean true"}
            {:async "1" :description "string '1'"}
            {:async 1 :description "number 1"}]]
      
      (doseq [{:keys [async description]} test-cases]
        (testing (str "async query param as " description)
          (let [response (http/post (str *base-url* "/account")
                                    {:query-params {:async async}
                                     :headers {"content-type" "application/json"}
                                     :body (json/write-value-as-string {:name (str "Test User " description)})
                                     :throw-exceptions false})
                body-str (:body response)
                body (when body-str (json/read-value body-str))]
            
            ;; For async requests, only "true" (string) and true (boolean) should trigger async mode
            ;; Other values like "1" or 1 are treated as sync according to the current implementation
            (if (or (= async "true") (= async true))
              (do
                (is (= 202 (:status response))
                    (str "Expected 202 status for async=" async " but got " (:status response)))
                (is (string? (get body "operation-id"))
                    (str "Expected operation-id for async=" async)))
              (do
                ;; Values like "1" or 1 are treated as sync (falsy for async)
                (is (= 200 (:status response))
                    (str "Expected 200 status for sync mode with async=" async " but got " (:status response)))
                (is (nil? (get body "operation-id"))
                    (str "Expected no operation-id for sync mode with async=" async))
                (is (nil? (get body "status"))
                    (str "Expected no status for sync mode with async=" async))))))))))

(deftest async-false-query-param-test
  (testing "async=false should behave like sync request"
    (let [response (http/post (str *base-url* "/account")
                              {:query-params {:async "false"}
                               :headers {"content-type" "application/json"}
                               :body (json/write-value-as-string {:name "Async False Test User"})
                               :throw-exceptions false})
          body-str (:body response)
          body (when body-str (json/read-value body-str))]
      
      ;; async=false should behave like sync (200 status, direct account response)
      (is (= 200 (:status response)))
      (is (= "Async False Test User" (get body "name")))
      (is (= 0 (get body "balance")))
      (is (> (get body "account-number") 0))
      (is (nil? (get body "operation-id"))) ; Should not have operation-id
      (is (nil? (get body "status"))))))

(deftest missing-async-query-param-test
  (testing "missing async query param should behave like sync request"
    (let [response (http/post (str *base-url* "/account")
                              {:headers {"content-type" "application/json"}
                               :body (json/write-value-as-string {:name "No Async Param Test User"})
                               :throw-exceptions false})
          body-str (:body response)
          body (when body-str (json/read-value body-str))]
      
      ;; Missing async param should behave like sync (200 status, direct account response)
      (is (= 200 (:status response)))
      (is (= "No Async Param Test User" (get body "name")))
      (is (= 0 (get body "balance")))
      (is (> (get body "account-number") 0))
      (is (nil? (get body "operation-id"))) ; Should not have operation-id
      (is (nil? (get body "status"))))))
