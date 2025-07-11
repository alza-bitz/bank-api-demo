(ns bank.system
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [bank.persistence.repository :as repo])
  (:gen-class))

(def system-config
  "System configuration for the banking application."
  {:db/datasource {:dbtype "postgresql"
                   :host "localhost"
                   :port 5432
                   :dbname "bankdb"
                   :user "bankuser"
                   :password "bankpass"}
   
   :bank.persistence.repository/repository {:datasource (ig/ref :db/datasource)}
   
   :bank.application.service/service {:repository (ig/ref :bank.persistence.repository/repository)}
   
   :bank.interface.http.handlers/handlers {:service (ig/ref :bank.application.service/service)}
   
   :bank.interface.http.routes/handler {:handlers (ig/ref :bank.interface.http.handlers/handlers)}
   
   :bank.interface.http.server/server {:handler (ig/ref :bank.interface.http.routes/handler)
                                       :port 3000}})

(defn read-config
  "Reads system configuration from environment variables or uses defaults."
  []
  (let [db-config {:dbtype "postgresql"
                   :host (or (System/getenv "DATABASE_HOST") "localhost")
                   :port (Integer/parseInt (or (System/getenv "DATABASE_PORT") "5432"))
                   :dbname (or (System/getenv "DATABASE_NAME") "bankdb")
                   :user (or (System/getenv "DATABASE_USER") "bankuser")
                   :password (or (System/getenv "DATABASE_PASSWORD") "bankpass")}
        http-port (Integer/parseInt (or (System/getenv "HTTP_PORT") "3000"))]
    (-> system-config
        (assoc-in [:db/datasource] db-config)
        (assoc-in [:bank.interface.http.server/server :port] http-port))))

(defn create-datasource
  "Creates a datasource for the given configuration."
  [config]
  (log/info "Creating datasource for" (select-keys config [:host :port :dbname :user]))
  config)

(defn init-database
  "Initializes the database schema."
  [datasource]
  (log/info "Initializing database schema")
  (try
    (repo/create-tables! datasource)
    (log/info "Database schema initialized successfully")
    datasource
    (catch Exception e
      (log/error e "Failed to initialize database schema")
      (throw e))))

;; Integrant methods for datasource
(defmethod ig/init-key :db/datasource [_ config]
  (let [datasource (create-datasource config)]
    (init-database datasource)))

(defmethod ig/halt-key! :db/datasource [_ _]
  (log/info "Datasource stopped")
  nil)

(def ^:private system-atom (atom nil))

(defn start-system!
  "Starts the system with the given configuration."
  ([]
   (start-system! (read-config)))
  ([config]
   (log/info "Starting banking system")
   (try
     (let [system (ig/init config)]
       (reset! system-atom system)
       (log/info "Banking system started successfully")
       system)
     (catch Exception e
       (log/error e "Failed to start banking system")
       (throw e)))))

(defn stop-system!
  "Stops the system."
  []
  (when-let [system @system-atom]
    (log/info "Stopping banking system")
    (try
      (ig/halt! system)
      (reset! system-atom nil)
      (log/info "Banking system stopped successfully")
      true
    (catch Exception e
      (log/error e "Error stopping banking system")
      false))))

(defn restart-system!
  "Restarts the system."
  []
  (stop-system!)
  (start-system!))

(defn add-shutdown-hook!
  "Adds a JVM shutdown hook to gracefully stop the system."
  []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. ^Runnable
            (fn []
              (log/info "Shutdown hook triggered")
              (stop-system!)))))

(defn -main
  "Main entry point for the banking application."
  [& _args]
  (log/info "Banking application starting")
  (try
    (start-system!)
    (add-shutdown-hook!)
    (log/info "Banking application is ready")
    ;; Keep the main thread alive
    @(promise)
    (catch Exception e
      (log/error e "Failed to start banking application")
      (System/exit 1))))
