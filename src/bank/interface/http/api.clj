(ns bank.interface.http.api
  (:require [malli.core :as m]
            [bank.domain.account :as domain]))

;; HTTP API Request/Response specs

;; Create account endpoint
(def create-account-request-spec
  [:map {:closed true}
   [:name domain/account-name-spec]])

(def create-account-response-spec
  [:map {:closed true}
   [:account-number domain/account-number-spec]
   [:name domain/account-name-spec]
   [:balance domain/account-balance-spec]])

;; View account endpoint  
(def view-account-response-spec
  [:map {:closed true}
   [:account-number domain/account-number-spec]
   [:name domain/account-name-spec]
   [:balance domain/account-balance-spec]])

;; Deposit endpoint
(def deposit-request-spec
  [:map {:closed true}
   [:amount [:int {:min 1}]]])

(def deposit-response-spec
  [:map {:closed true}
   [:account-number domain/account-number-spec]
   [:name domain/account-name-spec]
   [:balance domain/account-balance-spec]])

;; Withdraw endpoint
(def withdraw-request-spec
  [:map {:closed true}
   [:amount [:int {:min 1}]]])

(def withdraw-response-spec
  [:map {:closed true}
   [:account-number domain/account-number-spec]
   [:name domain/account-name-spec]
   [:balance domain/account-balance-spec]])

;; Transfer endpoint
(def transfer-request-spec
  [:map {:closed true}
   [:amount [:int {:min 1}]]
   [:account-number domain/account-number-spec]])

(def transfer-response-spec
  [:map {:closed true}
   [:account-number domain/account-number-spec]
   [:name domain/account-name-spec]
   [:balance domain/account-balance-spec]])

;; Audit endpoint
(def audit-response-item-spec
  [:map {:closed true}
   [:sequence [:int {:min 0}]]
   [:description [:string {:min 1}]]
   [:debit {:optional true} [:maybe [:int {:min 1}]]]
   [:credit {:optional true} [:maybe [:int {:min 1}]]]])

(def audit-response-spec
  [:vector audit-response-item-spec])

;; Error response
(def error-response-spec
  [:map {:closed true}
   [:error [:string {:min 1}]]
   [:message [:string {:min 1}]]])

;; Async operation response
(def operation-submit-response-spec
  [:map {:closed true}
   [:operation-id [:string {:min 1}]]])

;; Operation result response (wraps the actual operation result)
(def operation-result-response-spec
  [:map {:closed true}
   [:operation-id [:string {:min 1}]]
   [:status [:enum "completed" "error"]]
   [:result {:optional true} :any] ; The actual operation result
   [:error {:optional true} [:string {:min 1}]]
   [:message {:optional true} [:string {:min 1}]]])

;; Conversion functions
(defn account->response
  "Converts a saved account domain entity to HTTP response format."
  [account]
  {:account-number (:account-number account)
   :name (:name account)
   :balance (:balance account)})

(defn account-event->response
  "Converts an account event from the persistence layer to HTTP response format."
  [event]
  (cond-> {:sequence (:sequence event)
           :description (:description event)}
    (:debit event) (assoc :debit (:debit event))
    (:credit event) (assoc :credit (:credit event))))

(defn operation-id->submit-response
  "Converts an operation ID to an operation submit response."
  [operation-id]
  {:operation-id operation-id})

(defn operation-result->response
  "Converts an operation result to an operation result response."
  [operation-id result exception]
  (if exception
    {:operation-id operation-id
     :status "error"
     :error (name (:error (ex-data exception)))
     :message (.getMessage exception)}
    {:operation-id operation-id
     :status "completed"
     :result result}))

;; Validation functions
(defn valid-create-account-request? [request]
  (m/validate create-account-request-spec request))

(defn valid-create-account-response? [response]
  (m/validate create-account-response-spec response))

(defn valid-view-account-response? [response]
  (m/validate view-account-response-spec response))

(defn valid-deposit-request? [request]
  (m/validate deposit-request-spec request))

(defn valid-deposit-response? [response]
  (m/validate deposit-response-spec response))

(defn valid-withdraw-request? [request]
  (m/validate withdraw-request-spec request))

(defn valid-withdraw-response? [response]
  (m/validate withdraw-response-spec response))

(defn valid-transfer-request? [request]
  (m/validate transfer-request-spec request))

(defn valid-transfer-response? [response]
  (m/validate transfer-response-spec response))

(defn valid-audit-response? [response]
  (m/validate audit-response-spec response))

(defn valid-error-response? [response]
  (m/validate error-response-spec response))

(defn valid-operation-submit-response? [response]
  (m/validate operation-submit-response-spec response))

(defn valid-operation-result-response? [response]
  (m/validate operation-result-response-spec response))
