(ns bank.interface.http.handlers
  (:require [bank.application.service :as service]
            [bank.interface.http.api :as api]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn error-to-status-code
  "Maps error keywords to HTTP status codes."
  [error-key]
  (case error-key
    :account-not-found 404
    :insufficient-funds 422
    :same-account-transfer 422
    :invalid-account-number 400
    500)) ; default case

(defn handle-exception
  "Handles ExceptionInfo exceptions and converts them to HTTP responses."
  [e default-message]
  (if (instance? clojure.lang.ExceptionInfo e)
    (let [{:keys [error message] :as error-data} (ex-data e)]
      (log/error message error-data)
      {:status (error-to-status-code error)
       :body {:error (name error)
              :message message}})
    (do
      (log/error e default-message)
      {:status 500
       :body {:error "internal-server-error"
              :message default-message}})))

(defn create-account-handler
  "HTTP handler for creating bank accounts."
  [service]
  (fn [request]
    (try
      (let [body (:body-params request)
            name (:name body)]
        (log/info "HTTP: Creating account for" name)
        (let [account (service/create-account service name)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch Exception e
        (handle-exception e "HTTP: Error creating account")))))

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
      (catch Exception e
        (handle-exception e "HTTP: Error viewing account")))))

(defn deposit-handler
  "HTTP handler for depositing money to accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Depositing" amount "to account" account-number)
        (let [account (service/deposit-to-account service account-number amount)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error depositing to account")))))

(defn withdraw-handler
  "HTTP handler for withdrawing money from accounts."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Withdrawing" amount "from account" account-number)
        (let [account (service/withdraw-from-account service account-number amount)
              response (api/account->response account)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error withdrawing from account")))))

(defn transfer-handler
  "HTTP handler for transferring money between accounts."
  [service]
  (fn [request]
    (try
      (let [from-account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)
            to-account-number (:account-number body)]
        (log/info "HTTP: Transferring" amount "from account" from-account-number "to account" to-account-number)
        (let [result (service/transfer-between-accounts service from-account-number to-account-number amount)
              sender-account (:sender result)
              response (api/account->response sender-account)]
          (log/info "HTTP: Transfer successful, returning response:" response)
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (log/error e "HTTP: Exception in transfer handler")
        (handle-exception e "HTTP: Error transferring between accounts")))))

(defn audit-handler
  "HTTP handler for viewing account audit logs."
  [service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Getting audit log for account" account-number)
        (let [events (service/retrieve-account-audit service account-number)
              response (mapv api/account-event->response events)]
          {:status 200
           :body response}))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error getting audit log")))))

(defn make-handlers
  "Creates a map of all handlers with a given service."
  [service]
  {:create-account (create-account-handler service)
   :view-account (view-account-handler service)
   :deposit (deposit-handler service)
   :withdraw (withdraw-handler service)
   :transfer (transfer-handler service)
   :audit (audit-handler service)})

(defmethod ig/init-key :handlers/account [_ {:keys [service]}]
  (make-handlers service))
