(ns bank.interface.http.handlers
  (:require [bank.application.service :as service]
            [bank.interface.http.api :as api]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defn is-async-request?
  "Checks if the request should be handled asynchronously."
  [request]
  (= "true" (get-in request [:query-params "async"])))

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
  "HTTP handler for creating bank accounts. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [body (:body-params request)
            name (:name body)]
        (log/info "HTTP: Creating account for" name)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/create-account async-service name)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return account
          (let [account (service/create-account sync-service name)
                response (api/account->response account)]
            {:status 200
             :body response})))
      (catch Exception e
        (handle-exception e "HTTP: Error creating account")))))

(defn view-account-handler
  "HTTP handler for viewing bank accounts. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Viewing account" account-number)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/retrieve-account async-service account-number)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return account
          (let [account (service/retrieve-account sync-service account-number)
                response (api/account->response account)]
            {:status 200
             :body response})))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error viewing account")))))

(defn deposit-handler
  "HTTP handler for depositing money to accounts. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Depositing" amount "to account" account-number)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/deposit-to-account async-service account-number amount)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return account
          (let [account (service/deposit-to-account sync-service account-number amount)
                response (api/account->response account)]
            {:status 200
             :body response})))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error depositing to account")))))

(defn withdraw-handler
  "HTTP handler for withdrawing money from accounts. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)]
        (log/info "HTTP: Withdrawing" amount "from account" account-number)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/withdraw-from-account async-service account-number amount)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return account
          (let [account (service/withdraw-from-account sync-service account-number amount)
                response (api/account->response account)]
            {:status 200
             :body response})))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error withdrawing from account")))))

(defn transfer-handler
  "HTTP handler for transferring money between accounts. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [from-account-number (-> request :path-params :id Integer/parseInt)
            body (:body-params request)
            amount (:amount body)
            to-account-number (:account-number body)]
        (log/info "HTTP: Transferring" amount "from account" from-account-number "to account" to-account-number)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/transfer-between-accounts async-service from-account-number to-account-number amount)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return sender account
          (let [result (service/transfer-between-accounts sync-service from-account-number to-account-number amount)
                sender-account (:sender result)
                response (api/account->response sender-account)]
            (log/info "HTTP: Transfer successful, returning response:" response)
            {:status 200
             :body response})))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (log/error e "HTTP: Exception in transfer handler")
        (handle-exception e "HTTP: Error transferring between accounts")))))

(defn audit-handler
  "HTTP handler for viewing account audit logs. Supports both sync and async modes."
  [sync-service async-service]
  (fn [request]
    (try
      (let [account-number (-> request :path-params :id Integer/parseInt)]
        (log/info "HTTP: Getting audit log for account" account-number)
        (if (is-async-request? request)
          ;; Async mode - return operation ID
          (let [operation-id (service/retrieve-account-audit async-service account-number)
                response (api/operation-id->submit-response operation-id)]
            {:status 202
             :body response})
          ;; Sync mode - return audit events
          (let [events (service/retrieve-account-audit sync-service account-number)
                response (mapv api/account-event->response events)]
            {:status 200
             :body response})))
      (catch NumberFormatException e
        (log/warn e "HTTP: Invalid account number format")
        {:status 400
         :body {:error "bad-request"
                :message "Invalid account number format"}})
      (catch Exception e
        (handle-exception e "HTTP: Error getting audit log")))))

(defn operation-result-handler
  "HTTP handler for retrieving async operation results."
  [async-service]
  (fn [request]
    (try
      (let [operation-id (-> request :path-params :id)]
        (log/info "HTTP: Getting operation result for" operation-id)
        (let [result (service/retrieve-operation-result async-service operation-id)
              response (api/operation-result->response operation-id result nil)]
          {:status 200
           :body response}))
      (catch Exception e
        (let [operation-id (-> request :path-params :id)
              response (api/operation-result->response operation-id nil e)]
          {:status 200
           :body response})))))

(defn make-handlers
  "Creates a map of all handlers with sync and async services."
  [sync-service async-service]
  {:create-account (create-account-handler sync-service async-service)
   :view-account (view-account-handler sync-service async-service)
   :deposit (deposit-handler sync-service async-service)
   :withdraw (withdraw-handler sync-service async-service)
   :transfer (transfer-handler sync-service async-service)
   :audit (audit-handler sync-service async-service)
   :operation-result (operation-result-handler async-service)})

(defmethod ig/init-key ::handlers [_ {:keys [sync-service async-service]}]
  (make-handlers sync-service async-service))
