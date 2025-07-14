(ns bank.system-integration-test
  (:require
   [bank.system :as system]
   [bank.application.service]  ; Ensure service namespace is loaded
   [bank.persistence.repository] ; Ensure repository namespace is loaded
   [bank.interface.http.handlers] ; Ensure handlers namespace is loaded
   [bank.interface.http.routes]  ; Ensure routes namespace is loaded
   [bank.interface.http.server]  ; Ensure server namespace is loaded
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(def ^:dynamic *container* nil)

(defn ->test-config 
  "Creates test configuration using the postgres container."
  [container]
  (let [db-port (get (:mapped-ports container) 5432)]
    (-> (system/read-config)
        (assoc-in [:db/datasource :jdbcUrl] 
                  (str "jdbc:postgresql://localhost:" db-port "/testdb"))
        (assoc-in [:db/datasource :username] "testuser")
        (assoc-in [:db/datasource :password] "testpass")
        (assoc-in [:db/datasource :maximumPoolSize] 5)
        (assoc-in [:db/datasource :minimumIdle] 1)
        (assoc-in [:bank.interface.http.server/server :port] 0)))) ; Use random port

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

(deftest system-lifecycle-integration-test
  (testing "system can be started and stopped without errors"
    (let [config (->test-config *container*)
          system system/system-atom]
      
      (testing "system starts successfully"
        (is (do
              (system/start-system! config)
              true)
            "System should start without throwing exceptions")
        
        (is (some? @system)
            "System should return a non-nil value when started")
        
        (is (contains? @system :db/datasource)
            "System should contain datasource component")
        
        (is (contains? @system :bank.persistence.repository/repository)
            "System should contain repository component")
        
        (is (contains? @system :bank.application.service/service)
            "System should contain service component")
        
        (is (contains? @system :bank.interface.http.handlers/handlers)
            "System should contain handlers component")
        
        (is (contains? @system :bank.interface.http.routes/handler)
            "System should contain routes handler component")
        
        (is (contains? @system :bank.interface.http.server/server)
            "System should contain HTTP server component"))
      
      (testing "datasource is HikariCP instance"
        (let [datasource (get @system :db/datasource)]
          (is (instance? com.zaxxer.hikari.HikariDataSource datasource)
              "Datasource should be a HikariCP instance")
          
          (is (not (.isClosed datasource))
              "HikariCP datasource should be open/active")))
      
      (testing "system stops successfully"
        (with-redefs [system/system-atom (atom @system)]
          (is (system/stop-system!)
              "System should stop without errors")
          
          (is (nil? @system/system-atom)
              "System atom should be reset to nil after stopping")))
      
      (testing "datasource is closed after system stop"
        (let [datasource (get @system :db/datasource)]
          (is (.isClosed datasource)
              "HikariCP datasource should be closed after system stop"))))))

(deftest system-configuration-integration-test
  (testing "system respects configuration"
    (let [custom-config (-> (->test-config *container*)
                            (assoc-in [:db/datasource :maximumPoolSize] 3)
                            (assoc-in [:db/datasource :minimumIdle] 1))
          system (system/start-system! custom-config)]
      
      (try
        (testing "HikariCP uses configured pool size"
          (let [datasource (get system :db/datasource)]
            (is (= 3 (.getMaximumPoolSize datasource))
                "Maximum pool size should match configuration")
            
            (is (= 1 (.getMinimumIdle datasource))
                "Minimum idle should match configuration")))
        
        (finally
          (with-redefs [system/system-atom (atom system)]
            (system/stop-system!)))))))

(deftest system-error-handling-integration-test
  (testing "system handles startup errors gracefully"
    (let [invalid-config (-> (system/read-config)
                             (assoc-in [:db/datasource :jdbcUrl] "jdbc:postgresql://invalid-host:5432/invalid")
                             (assoc-in [:bank.interface.http.server/server :port] 0))]
      
      (testing "invalid database configuration throws exception"
        (is (thrown? Exception
                     (system/start-system! invalid-config))
            "System should throw exception for invalid database config")))))
