(ns bank.domain.account
  (:require [malli.core :as m]
            [malli.generator :as mg]))

;; Account entity specs
(def account-number-spec
  [:int {:min 1}])

(def account-name-spec
  [:string {:min 1 :max 255}])

(def account-balance-spec
  [:int {:min 0}])

(def account-spec
  [:map
   [:account-number account-number-spec]
   [:name account-name-spec]
   [:balance account-balance-spec]])

;; Account event entity specs
(def event-id-spec
  [:int {:min 1}])

(def event-description-spec
  [:string {:min 1 :max 255}])

(def account-event-spec
  [:map
   [:event-id {:optional true} event-id-spec]
   [:account-number account-number-spec]
   [:description event-description-spec]
   [:timestamp inst?]])

;; Domain functions
(defn create-account
  "Creates a new account with the given name and initial balance of 0."
  [{:keys [account-number name]}]
  {:pre [(m/validate account-number-spec account-number)
         (m/validate account-name-spec name)]}
  {:account-number account-number
   :name name
   :balance 0})

(defn create-account-event
  "Creates a domain event for account operations."
  [account-number description]
  {:pre [(m/validate account-number-spec account-number)
         (m/validate event-description-spec description)]}
  {:account-number account-number
   :description description
   :timestamp (java.time.Instant/now)})

;; Validation functions
(defn valid-account? [account]
  (m/validate account-spec account))

(defn valid-account-event? [event]
  (m/validate account-event-spec event))

;; Generator functions for testing
(defn gen-account-name []
  (mg/generate account-name-spec))

(defn gen-account []
  (mg/generate account-spec))

(defn gen-account-event []
  (mg/generate account-event-spec))
