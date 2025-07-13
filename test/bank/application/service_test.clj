(ns bank.application.service-test
  (:require [clojure.test :refer [deftest testing is]]
            [bank.application.service :as service]
            [bank.persistence.repository :as repo]
            [spy.core :as spy]))

(deftest create-account-test
  (testing "creates account through service"
    (let [saved-account {:id (random-uuid)
                         :account-number 1
                         :name "Jane Doe"
                         :balance 0}
          save-account-spy (spy/spy (constantly saved-account))
          mock-repo (reify repo/AccountRepository
                      (save-account [_this account]
                        (save-account-spy account))
                      (save-account-events [_this _account-event-pairs] nil)
                      (save-account-event [_this account _event] account)
                      (find-account [_this _account-number] nil)
                      (find-account-events [_this _account-number] []))
          service (service/->SyncAccountService mock-repo)
          created-account (service/create-account service "Jane Doe")]

      (is (= saved-account created-account))
      (is (spy/called-once? save-account-spy)))))

(deftest retrieve-account-test
  (testing "retrieves account through service"
    (let [existing-account {:id (random-uuid)
                            :account-number 456
                            :name "Bob Smith"
                            :balance 200}
          find-account-spy (spy/spy (constantly existing-account))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account)
                      (save-account-events [_this _account-event-pairs] nil)
                      (find-account-events [_this _account-number] []))
          service (service/->SyncAccountService mock-repo)
          retrieved-account (service/retrieve-account service 456)]

      (is (= existing-account retrieved-account))
      (is (spy/called-once? find-account-spy))
      (is (spy/called-with? find-account-spy 456))))
  
  (testing "throws exception for non-existent account"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (throw (ex-info "Account not found" 
                                                    {:error :account-not-found 
                                                     :account-number account-number}))))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)]
  
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/retrieve-account service 999)))
      (is (spy/called-once? find-account-spy)))))

(deftest deposit-to-account-test
  (testing "deposits to existing account"
    (let [existing-account {:id (random-uuid)
                            :account-number 123
                            :name "John Doe"
                            :balance 50}
          find-account-spy (spy/spy (constantly existing-account))
          save-account-event-spy (spy/spy (fn [_repo account _event] account))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      ;; (save-account [_this account]
                      ;;   account)
                      (save-account-event [this account event]
                        (save-account-event-spy this account event)))
          service (service/->SyncAccountService mock-repo)
          updated-account (service/deposit-to-account service 123 100)]

      (is (= 150 (:balance updated-account)))
      (is (spy/called-once? find-account-spy))
      (is (spy/called-with? find-account-spy 123))
      (is (spy/called-once? save-account-event-spy))))

  (testing "throws exception for non-existent account"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (throw (ex-info "Account not found" 
                                                    {:error :account-not-found 
                                                     :account-number account-number}))))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/deposit-to-account service 999 100)))
      (is (spy/called-once? find-account-spy)))))

(deftest withdraw-from-account-test
  (testing "withdraws from existing account with sufficient funds"
    (let [existing-account {:id (random-uuid)
                            :account-number 123
                            :name "John Doe"
                            :balance 200}
          find-account-spy (spy/spy (constantly existing-account))
          save-account-event-spy (spy/spy (fn [_repo account _event] account))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [this account event]
                        (save-account-event-spy this account event)))
          service (service/->SyncAccountService mock-repo)
          updated-account (service/withdraw-from-account service 123 75)]

      (is (= 125 (:balance updated-account)))
      (is (spy/called-once? find-account-spy))
      (is (spy/called-with? find-account-spy 123))
      (is (spy/called-once? save-account-event-spy))))

  (testing "throws exception for insufficient funds"
    (let [existing-account {:id (random-uuid)
                            :account-number 123
                            :name "John Doe"
                            :balance 50}
          find-account-spy (spy/spy (constantly existing-account))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Insufficient funds"
           (service/withdraw-from-account service 123 100)))
      (is (spy/called-once? find-account-spy))
      (is (= :insufficient-funds 
             (-> (try (service/withdraw-from-account service 123 100)
                      (catch clojure.lang.ExceptionInfo e (ex-data e)))
                 :error)))))

  (testing "throws exception for non-existent account"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (throw (ex-info "Account not found" 
                                                    {:error :account-not-found 
                                                     :account-number account-number}))))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/withdraw-from-account service 999 100)))
      (is (spy/called-once? find-account-spy)))))

(deftest retrieve-account-audit-test
  (testing "retrieves account audit log through service"
    (let [existing-account {:id (random-uuid)
                            :account-number 123
                            :name "John Doe"
                            :balance 200}
          account-events [{:id (random-uuid)
                           :sequence 2
                           :account-number 123
                           :description "withdraw"
                           :debit 50
                           :credit nil
                           :timestamp (java.sql.Timestamp/from (java.time.Instant/now))}
                          {:id (random-uuid)
                           :sequence 1
                           :account-number 123
                           :description "deposit"
                           :debit nil
                           :credit 100
                           :timestamp (java.sql.Timestamp/from (java.time.Instant/now))}]
          find-account-spy (spy/spy (constantly existing-account))
          find-account-events-spy (spy/spy (constantly account-events))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (find-account-events [_this account-number]
                        (find-account-events-spy account-number))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)
          audit-events (service/retrieve-account-audit service 123)]

      (is (= account-events audit-events))
      (is (spy/called-once? find-account-spy))
      (is (spy/called-with? find-account-spy 123))
      (is (spy/called-once? find-account-events-spy))
      (is (spy/called-with? find-account-events-spy 123))))

  (testing "throws exception for non-existent account"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (throw (ex-info "Account not found" 
                                                    {:error :account-not-found 
                                                     :account-number account-number}))))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (find-account-events [_this _account-number] [])
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/retrieve-account-audit service 999)))
      (is (spy/called-once? find-account-spy)))))

(deftest transfer-between-accounts-test
  (testing "transfers money between accounts through service"
    (let [sender-account {:id (random-uuid) :account-number 1 :name "Sender" :balance 100}
          receiver-account {:id (random-uuid) :account-number 2 :name "Receiver" :balance 50}
          find-account-spy (spy/spy (fn [account-number]
                                     (case account-number
                                       1 sender-account
                                       2 receiver-account)))
          save-account-events-spy (spy/spy (constantly nil))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account-events [_this account-event-pairs]
                        (save-account-events-spy account-event-pairs))
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account)
                      (find-account-events [_this _account-number] []))
          service (service/->SyncAccountService mock-repo)
          result (service/transfer-between-accounts service 1 2 30)]

      ;; Test sender account in result
      (is (= 70 (:balance (:sender result))))
      (is (= 1 (:account-number (:sender result))))
      (is (= "Sender" (:name (:sender result))))
      
      ;; Test receiver account in result
      (is (= 80 (:balance (:receiver result))))
      (is (= 2 (:account-number (:receiver result))))
      (is (= "Receiver" (:name (:receiver result))))
      
      ;; Test repository interactions
      (is (spy/called-with? find-account-spy 1))
      (is (spy/called-with? find-account-spy 2))
      (is (spy/called-once? save-account-events-spy))))

  (testing "throws exception when sender account not found"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (if (= account-number 1)
                                       (throw (ex-info "Account not found" 
                                                      {:error :account-not-found 
                                                       :account-number account-number}))
                                       {:id (random-uuid) :account-number 2 :name "Receiver" :balance 50})))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account-events [_this _account-event-pairs] nil)
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account)
                      (find-account-events [_this _account-number] []))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/transfer-between-accounts service 1 2 30)))
      (is (spy/called-once? find-account-spy))))

  (testing "throws exception when receiver account not found"
    (let [find-account-spy (spy/spy (fn [account-number]
                                     (if (= account-number 1)
                                       {:id (random-uuid) :account-number 1 :name "Sender" :balance 100}
                                       (throw (ex-info "Account not found" 
                                                      {:error :account-not-found 
                                                       :account-number account-number})))))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number))
                      (save-account-events [_this _account-event-pairs] nil)
                      (save-account [_this account] account)
                      (save-account-event [_this account _event] account)
                      (find-account-events [_this _account-number] []))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/transfer-between-accounts service 1 2 30)))
      (is (spy/called-n-times? find-account-spy 2)))))
