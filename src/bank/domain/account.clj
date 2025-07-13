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

;; a map that either has a :debit key or a :credit key, but not both
(def event-action-spec
  [:or
   [:map {:closed true} [:debit [:int {:min 1}]]]
   [:map {:closed true} [:credit [:int {:min 1}]]]])

(def account-event-spec
  [:map
   [:id [:uuid]]
   [:description event-description-spec]
   [:timestamp inst?]
   [:action event-action-spec]])

(def saved-account-event-spec
  [:and
   [:map
    [:id [:uuid]]
    [:description event-description-spec]
    [:timestamp inst?]
    [:sequence event-sequence-spec]
    [:account-number account-number-spec]
    [:credit {:optional true} [:int {:min 1}]]
    [:debit {:optional true} [:int {:min 1}]]]
   ;; Ensure exactly one of credit or debit is present
   [:fn (fn [event]
          (let [has-credit (contains? event :credit)
                has-debit (contains? event :debit)]
            (and (or has-credit has-debit)
                 (not (and has-credit has-debit)))))]])

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
  [description action]
  {:pre [(m/validate event-description-spec description)
         (m/validate event-action-spec action)]}
  {:id (random-uuid)
   :description description
   :timestamp (java.time.Instant/now)
   :action action})

(def account-update-spec
  [:map
   [:account account-spec]
   [:event account-event-spec]])

(defn deposit
  "Deposits amount to an account and returns an account update.
   Amount must be positive."
  [account amount]
  {:pre [(m/validate account-spec account)
         (pos? amount)]}
  (let [updated-account (update account :balance + amount)
        deposit-event (create-account-event "deposit" {:credit amount})]
    {:account updated-account
     :event deposit-event}))

(defn withdraw
  "Withdraws amount from an account and returns an account update.
   Amount must be positive and resulting balance must not be negative."
  [account amount]
  {:pre [(m/validate account-spec account)
         (pos? amount)]}
  (when (< (:balance account) amount)
    (throw (ex-info "Insufficient funds" {:error :insufficient-funds 
                                          :balance (:balance account) 
                                          :amount amount})))
  (let [updated-account (update account :balance - amount)
        withdraw-event (create-account-event "withdraw" {:debit amount})]
    {:account updated-account
     :event withdraw-event}))

;; Validation functions
(defn valid-account? [account]
  (m/validate account-spec account))

(defn valid-saved-account? [account]
  (m/validate saved-account-spec account))

(defn valid-account-event? [event]
  (m/validate account-event-spec event))

(defn valid-saved-account-event? [event]
  (m/validate saved-account-event-spec event))

(defn valid-account-update? [account-update]
  (m/validate account-update-spec account-update))

;; Generator functions for testing
(defn gen-account-name []
  (mg/generate account-name-spec))

(defn gen-account []
  (mg/generate account-spec))

(defn gen-account-event []
  (mg/generate account-event-spec))
