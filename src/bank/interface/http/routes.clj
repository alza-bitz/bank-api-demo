(ns bank.interface.http.routes
  (:require [reitit.ring :as ring]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.coercion.malli :as malli]
            [reitit.ring.coercion :as coercion]
            [muuntaja.core :as m]
            [integrant.core :as ig]
            [bank.interface.http.api :as api]))

(def routes-data
  "Base route data with common middleware and coercion."
  {:muuntaja m/instance
   :coercion malli/coercion
   :middleware [openapi/openapi-feature
                parameters/parameters-middleware
                muuntaja/format-negotiate-middleware
                muuntaja/format-response-middleware
                exception/exception-middleware
                muuntaja/format-request-middleware
                coercion/coerce-response-middleware
                coercion/coerce-request-middleware]})

(defn create-routes
  "Creates Reitit routes with the given handlers."
  [handlers]
  [["" routes-data
    ["/swagger.json"
     {:get {:no-doc true
            :swagger {:info {:title "Banking API"
                             :description "HTTP API for managing banking accounts"
                             :version "1.0.0"}}
            :handler (openapi/create-openapi-handler)}}]

    ["/account"
     {:swagger {:tags ["accounts"]}}

     [""
      {:post {:summary "Create a bank account"
              :description "Creates a new bank account with the given name and initial balance of 0. Use query parameter async=true for asynchronous processing."
              :parameters {:body api/create-account-request-spec}
              :responses {200 {:body api/create-account-response-spec
                               :description "Account created successfully (sync mode)"}
                          202 {:body api/operation-submit-response-spec
                               :description "Operation submitted successfully (async mode)"}
                          400 {:body api/error-response-spec
                               :description "Invalid request"}
                          500 {:body api/error-response-spec
                               :description "Internal server error"}}
              :handler (:create-account handlers)}}]

     ["/:id"
      {:get {:summary "View a bank account"
             :description "Retrieves an existing bank account by account number. Use query parameter async=true for asynchronous processing."
             :parameters {:path [:map [:id :int]]}
             :responses {200 {:body api/view-account-response-spec
                              :description "Account retrieved successfully (sync mode)"}
                         202 {:body api/operation-submit-response-spec
                              :description "Operation submitted successfully (async mode)"}
                         400 {:body api/error-response-spec
                              :description "Invalid account number"}
                         404 {:body api/error-response-spec
                              :description "Account not found"}
                         500 {:body api/error-response-spec
                              :description "Internal server error"}}
             :handler (:view-account handlers)}}]

     ["/:id/deposit"
      {:post {:summary "Deposit money to an account"
              :description "Deposits a positive amount of money to an existing bank account"
              :parameters {:path [:map [:id :int]]
                           :body api/deposit-request-spec}
              :responses {200 {:body api/deposit-response-spec
                               :description "Deposit completed successfully"}
                          202 {:body api/operation-submit-response-spec
                               :description "Operation submitted successfully (async mode)"}
                          400 {:body api/error-response-spec
                               :description "Invalid request"}
                          404 {:body api/error-response-spec
                               :description "Account not found"}
                          500 {:body api/error-response-spec
                               :description "Internal server error"}}
              :handler (:deposit handlers)}}]

     ["/:id/withdraw"
      {:post {:summary "Withdraw money from an account"
              :description "Withdraws a positive amount of money from an existing bank account. The resulting balance must not fall below zero."
              :parameters {:path [:map [:id :int]]
                           :body api/withdraw-request-spec}
              :responses {200 {:body api/withdraw-response-spec
                               :description "Withdrawal completed successfully"}
                          202 {:body api/operation-submit-response-spec
                               :description "Operation submitted successfully (async mode)"}
                          400 {:body api/error-response-spec
                               :description "Invalid request or insufficient funds"}
                          404 {:body api/error-response-spec
                               :description "Account not found"}
                          500 {:body api/error-response-spec
                               :description "Internal server error"}}
              :handler (:withdraw handlers)}}]

     ["/:id/send"
      {:post {:summary "Transfer money to another account"
              :description "Transfers money from one existing account to another existing account. You cannot transfer money from an account to itself. The resulting balance of the sending account must not fall below zero."
              :parameters {:path [:map [:id :int]]
                           :body api/transfer-request-spec}
              :responses {200 {:body api/transfer-response-spec
                               :description "Transfer completed successfully"}
                          202 {:body api/operation-submit-response-spec
                               :description "Operation submitted successfully (async mode)"}
                          400 {:body api/error-response-spec
                               :description "Invalid request, insufficient funds, or same account transfer"}
                          404 {:body api/error-response-spec
                               :description "Account not found"}
                          500 {:body api/error-response-spec
                               :description "Internal server error"}}
              :handler (:transfer handlers)}}]

     ["/:id/audit"
      {:get {:summary "Retrieve account audit log"
             :description "Retrieves the audit log of an account. The audit log consists of records describing the events on the account in reverse chronological order."
             :parameters {:path [:map [:id :int]]}
             :responses {200 {:body api/audit-response-spec
                              :description "Audit log retrieved successfully"}
                         202 {:body api/operation-submit-response-spec
                              :description "Operation submitted successfully (async mode)"}
                         400 {:body api/error-response-spec
                              :description "Invalid request"}
                         404 {:body api/error-response-spec
                              :description "Account not found"}
                         500 {:body api/error-response-spec
                              :description "Internal server error"}}
             :handler (:audit handlers)}}]]

    ["/operation"
     {:swagger {:tags ["operations"]}}

     ["/:id"
      {:get {:summary "Retrieve async operation result"
             :description "Retrieves the result of an asynchronous operation by operation ID"
             :parameters {:path [:map [:id :string]]}
             :responses {200 {:body api/operation-result-response-spec
                              :description "Operation result retrieved successfully"}
                         400 {:body api/error-response-spec
                              :description "Invalid operation ID"}
                         500 {:body api/error-response-spec
                              :description "Internal server error"}}
             :handler (:operation-result handlers)}}]]]])

(defn create-router
  "Creates a Reitit router with the given handlers."
  [handlers]
  (ring/router (create-routes handlers)))

;; create-handler
(defn create-handler
  "Creates a Ring application with the given handlers."
  [handlers]
  (ring/ring-handler
   (create-router handlers)
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/swagger"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

;; Integrant methods
(defmethod ig/init-key ::router [_ {:keys [handlers]}]
  (create-router handlers))

(defmethod ig/init-key ::handler [_ {:keys [handlers]}]
  (create-handler handlers))

(defmethod ig/halt-key! ::router [_ _]
  ;; No cleanup needed for router
  nil)

(defmethod ig/halt-key! ::handler [_ _]
  ;; No cleanup needed for handler
  nil)
