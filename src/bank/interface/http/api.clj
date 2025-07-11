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

;; Error response
(def error-response-spec
  [:map {:closed true}
   [:error [:string {:min 1}]]
   [:message [:string {:min 1}]]])

;; Conversion functions
(defn account->response
  "Converts a saved account domain entity to HTTP response format."
  [account]
  {:account-number (:account-number account)
   :name (:name account)
   :balance (:balance account)})

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

(defn valid-error-response? [response]
  (m/validate error-response-spec response))
