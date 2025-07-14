(ns bank.system
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [bank.persistence.repository :as repo])
  (:gen-class)
  (:import [com.zaxxer.hikari HikariDataSource HikariConfig]))

(def default-config
  "System configuration for the banking application."

  {:db/datasource {}
   
   :bank.persistence.repository/repository {:datasource (ig/ref :db/datasource)}
   
   :bank.application.service/service {:repository (ig/ref :bank.persistence.repository/repository)}
   
   :bank.interface.http.handlers/handlers {:service (ig/ref :bank.application.service/service)}
   
   :bank.interface.http.routes/handler {:handlers (ig/ref :bank.interface.http.handlers/handlers)}
   
   :bank.interface.http.server/server {:handler (ig/ref :bank.interface.http.routes/handler)
                                       :port 3000}})

(defn read-config
  "Reads system configuration from environment variables or uses defaults."
  []
  (let [db-host (or (System/getenv "DATABASE_HOST") "localhost")
        db-port (or (System/getenv "DATABASE_PORT") "5432")
        db-name (or (System/getenv "DATABASE_NAME") "bankdb")
        db-user (or (System/getenv "DATABASE_USER") "bankuser")
        db-password (or (System/getenv "DATABASE_PASSWORD") "bankpass")
        jdbc-url (str "jdbc:postgresql://" db-host ":" db-port "/" db-name)
        hikari-config {:jdbcUrl jdbc-url
                       :username db-user
                       :password db-password
                       :maximumPoolSize (Integer/parseInt (or (System/getenv "DB_MAX_POOL_SIZE") "10"))
                       :minimumIdle (Integer/parseInt (or (System/getenv "DB_MIN_IDLE") "2"))
                       :connectionTimeout (Integer/parseInt (or (System/getenv "DB_CONNECTION_TIMEOUT") "30000"))
                       :idleTimeout (Integer/parseInt (or (System/getenv "DB_IDLE_TIMEOUT") "600000"))
                       :maxLifetime (Integer/parseInt (or (System/getenv "DB_MAX_LIFETIME") "1800000"))}
        http-port (Integer/parseInt (or (System/getenv "HTTP_PORT") "3000"))]
    (-> default-config
        (assoc-in [:db/datasource] hikari-config)
        (assoc-in [:bank.interface.http.server/server :port] http-port))))

(defn create-hikari-datasource
  "Creates a HikariCP datasource from the configuration."
  [hikari-config]
  (log/info "Creating HikariCP datasource with config:" (dissoc hikari-config :password))
  (let [config (HikariConfig.)]
    (doseq [[k v] hikari-config]
      (case k
        :jdbcUrl (.setJdbcUrl config v)
        :username (.setUsername config v)
        :password (.setPassword config v)
        :maximumPoolSize (.setMaximumPoolSize config v)
        :minimumIdle (.setMinimumIdle config v)
        :connectionTimeout (.setConnectionTimeout config v)
        :idleTimeout (.setIdleTimeout config v)
        :maxLifetime (.setMaxLifetime config v)
        (log/warn "Unknown HikariCP config key:" k)))
    (HikariDataSource. config)))

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
(defmethod ig/init-key :db/datasource [_ hikari-config]
  (log/info "Initializing HikariCP configuration")
  (let [datasource (create-hikari-datasource hikari-config)]
    (init-database datasource)))

(defmethod ig/halt-key! :db/datasource [_ datasource]
  (log/info "Closing HikariCP datasource")
  (when datasource
    (.close datasource))
  (log/info "HikariCP configuration stopped")
  nil)

(def system-atom (atom nil))

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
