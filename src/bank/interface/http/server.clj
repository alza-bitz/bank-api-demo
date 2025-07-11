(ns bank.interface.http.server
  (:require [ring.adapter.jetty :as jetty]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]))

(defrecord HttpServer [handler port server]
  Object
  (toString [_] (str "HttpServer on port " port)))

(defn start-server
  "Starts HTTP server with the given handler and port."
  [handler port]
  (log/info "Starting HTTP server on port" port)
  (let [server (jetty/run-jetty handler {:port port :join? false})]
    (log/info "HTTP server started successfully on port" port)
    (->HttpServer handler port server)))

(defn stop-server
  "Stops the HTTP server."
  [{:keys [server port] :as http-server}]
  (when server
    (log/info "Stopping HTTP server on port" port)
    (.stop server)
    (log/info "HTTP server stopped successfully"))
  http-server)

;; Integrant methods
(defmethod ig/init-key ::server [_ {:keys [handler port]}]
  (start-server handler (or port 3000)))

(defmethod ig/halt-key! ::server [_ server]
  (stop-server server)
  nil)
