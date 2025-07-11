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
                        (save-account-spy account)))
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
                        (find-account-spy account-number)))
          service (service/->SyncAccountService mock-repo)
          retrieved-account (service/retrieve-account service 456)]

      (is (= existing-account retrieved-account))
      (is (spy/called-once? find-account-spy))
      (is (spy/called-with? find-account-spy 456))))
  
  (testing "throws exception for non-existent account"
    (let [find-account-spy (spy/spy (constantly nil))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number)))
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
    (let [find-account-spy (spy/spy (constantly nil))
          mock-repo (reify repo/AccountRepository
                      (find-account [_this account-number]
                        (find-account-spy account-number)))
          service (service/->SyncAccountService mock-repo)]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Account not found"
           (service/deposit-to-account service 999 100)))
      (is (spy/called-once? find-account-spy)))))

