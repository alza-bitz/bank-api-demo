(ns user
  (:require
   [bank.domain.account :as account]
   [bank.application.service :as service]
   [bank.system :as system]
   [clojure.tools.namespace.repl :as repl]))

;; Example interactions with the domain layer showing various features
(comment
  (def my-account (account/create-account "It's me")))

(comment
  (account/deposit my-account 10))

(comment
  (-> my-account
      (account/deposit 100)
      :account
      (account/withdraw 50)
      :account
      (account/deposit 100)
      :account))

(comment
  ;; Better approach - capture both final account and all events
  (let [result1 (account/deposit my-account 100)
        result2 (account/withdraw (:account result1) 50)
        result3 (account/deposit (:account result2) 100)]
    {:final-account (:account result3)
     :all-events [(:event result1) (:event result2) (:event result3)]}))

(comment
  ;; Or use a helper function to accumulate events
  (defn chain-operations [account & operations]
    (reduce (fn [{:keys [final-account all-events]} operation]
              (let [result (operation final-account)]
                {:final-account (:account result)
                 :all-events (conj all-events (:event result))}))
            {:final-account account :all-events []}
            operations)))

(comment
  (chain-operations my-account
                    #(account/deposit % 100)
                    #(account/withdraw % 50)
                    #(account/deposit % 100)))

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
  (def service
    (-> system/system-atom
        deref
        :bank.application.service/sync-service)))

;; Example interactions with the service layer of a running system showing various features
(comment
  (service/retrieve-account service 1)
  (service/create-account service "It's me!")
  (service/deposit-to-account service 1 1)
  (service/withdraw-from-account service 1 1)
  (service/withdraw-from-account service 1 1)
  (service/retrieve-account-audit service 1))

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
  (clojure.test/run-tests 'bank.interface.http.api-test))

(comment
  (clojure.test/run-tests 'bank.interface.http.integration-test))

(comment 
  (repl/refresh))
