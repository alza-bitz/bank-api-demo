(ns bank.domain.account
  (:require [malli.core :as m]
            [malli.generator :as mg]
            [malli.util :as mu]))

;; Account entity specs
(def account-number-spec
  [:int {:min 1}])

(def account-name-spec
  [:string {:min 1 :max 255}])

(def account-balance-spec
  [:int {:min 0}])

(def account-spec
  [:map
   [:id [:uuid]]
   [:account-number {:optional true} account-number-spec]
   [:name account-name-spec]
   [:balance account-balance-spec]])

(def saved-account-spec
  (mu/merge
    account-spec
    [:map [:account-number account-number-spec]]))

;; Account event entity specs
(def event-sequence-spec
  [:int {:min 1}])

(def event-description-spec
  [:string {:min 1 :max 255}])

(def account-event-spec
  [:map
   [:id [:uuid]]
   [:sequence {:optional true} event-sequence-spec]
   [:account-number account-number-spec]
   [:description event-description-spec]
   [:timestamp inst?]])

(def saved-account-event-spec
  (mu/merge
   account-event-spec
   [:map [:sequence event-sequence-spec]]))

;; Domain functions
(defn create-account
  "Creates a new account with the given name and initial balance of 0."
  [name]
  {:pre [(m/validate account-name-spec name)]}
  {:id (random-uuid)
   :name name
   :balance 0})

(defn create-account-event
  "Creates a domain event for account operations."
  [account-number description]
  {:pre [(m/validate account-number-spec account-number)
         (m/validate event-description-spec description)]}
  {:id (random-uuid)
   :account-number account-number
   :description description
   :timestamp (java.time.Instant/now)})

;; Validation functions
(defn valid-account? [account]
  (m/validate account-spec account))

(defn valid-saved-account? [account]
  (m/validate saved-account-spec account))

(defn valid-account-event? [event]
  (m/validate account-event-spec event))

(defn valid-saved-account-event? [event]
  (m/validate saved-account-event-spec event))

;; Generator functions for testing
(defn gen-account-name []
  (mg/generate account-name-spec))

(defn gen-account []
  (mg/generate account-spec))

(defn gen-account-event []
  (mg/generate account-event-spec))
