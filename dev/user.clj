(ns user
  (:require [bank.system]
            [clojure.tools.namespace.repl :as repl]))

;; System management helpers for REPL development
(def start system/start-system!)
(def stop system/stop-system!)
(def restart system/restart-system!)

(defn reset []
  "Stop the system, refresh namespaces, and restart the system."
  (stop)
  (repl/refresh :after 'user/start))

(comment
  ;; System management
  (start)
  (stop)
  (restart)
  (reset))

(comment
  (clojure.test/run-tests 'bank.domain.account-test))

(comment
  (clojure.test/run-tests 'bank.persistence.repository-test))

(comment
  (clojure.test/run-tests 'bank.persistence.repository-integration-test))

(comment
  (clojure.test/run-tests 'bank.application.service-test))

(comment
  (clojure.test/run-tests 'bank.application.service-integration-test))

(comment
  (clojure.test/run-tests 'bank.interface.http.integration-test))

(comment
  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (clojure.tools.namespace.repl/refresh))
