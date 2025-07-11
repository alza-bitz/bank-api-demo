(ns bank.interface.http.handlers
  (:require [bank.application.service :as service]
            [bank.interface.http.api :as api]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn create-account-handler
  "HTTP handler for creating bank accounts."
  [service]
  (fn [request]
    (try
      (let [body (:body-params request)
            name (:name body)]
        (log/info "HTTP: Creating account for" name)
        (if (api/valid-create-account-request? body)
          (let [account (service/create-account service name)
                response (api/account->response account)]
            {:status 200
             :body response})
          {:status 400
           :body {:error "bad-request"
                  :message "Invalid request body"}}))
      (catch Exception e
        (log/error e "HTTP: Error creating account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to create account"}}))))

(defn view-account-handler
  "HTTP handler for viewing bank accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Viewing account" account-number)
        (let [account (service/retrieve-account service account-number)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (if (= "Account not found" (.getMessage e))
          (do
            (log/warn "HTTP: Account not found" (:account-number (ex-data e)))
            {:status 404
             :body {:error "not-found"
                    :message "Account not found"}})
          (do
            (log/error e "HTTP: Error viewing account")
            {:status 500
             :body {:error "internal-server-error"
                    :message "Failed to retrieve account"}})))
      (catch Exception e
        (log/error e "HTTP: Error viewing account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to retrieve account"}}))))

(defn deposit-handler
  "HTTP handler for depositing money to accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Depositing" amount "to account" account-number)
        (if (api/valid-deposit-request? body)
          (let [account (service/deposit-to-account service account-number amount)
                response (api/account->response account)]
            {:status 200
             :body response})
          {:status 400
           :body {:error "bad-request"
                  :message "Invalid request body"}}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch clojure.lang.ExceptionInfo e
        (if (= "Account not found" (.getMessage e))
          (do
            (log/warn "HTTP: Account not found" (:account-number (ex-data e)))
            {:status 404
             :body {:error "not-found"
                    :message "Account not found"}})
          (do
            (log/error e "HTTP: Error depositing to account")
            {:status 500
             :body {:error "internal-server-error"
                    :message "Failed to deposit to account"}})))
      (catch Exception e
        (log/error e "HTTP: Error depositing to account")
        {:status 500
         :body {:error "internal-server-error"
                :message "Failed to deposit to account"}}))))

(defrecord HttpHandlers [service]
  Object
  (toString [_] "HttpHandlers"))

(defn make-handlers
  "Creates HTTP handlers with service dependency."
  [service]
  {:create-account (create-account-handler service)
   :view-account (view-account-handler service)
   :deposit (deposit-handler service)})

;; Integrant methods
(defmethod ig/init-key ::handlers [_ {:keys [service]}]
  (make-handlers service))

(defmethod ig/halt-key! ::handlers [_ _]
  ;; No cleanup needed for handlers
  nil)
