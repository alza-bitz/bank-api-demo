(ns bank.persistence.repository-test
  (:require
   [bank.domain.account :as account]
   [bank.persistence.repository :as repo]
   [clojure.test :refer [deftest is testing]]
   [spy.assert :as spy-assert]
   [spy.core :as spy]))

(deftest jdbc-repository-test
  (testing "create-account calls repository protocol correctly"
    (let [mock-repo (reify repo/AccountRepository
                      (create-account [_ name]
                        {:account-number 1
                         :name name
                         :balance 0})
                      (find-account [_ _] nil)
                      (save-account-event [_ _ _] nil))]
      (let [account (repo/create-account mock-repo "Mr. Black")]
        (is (account/valid-account? account))
        (is (= 1 (:account-number account)))
        (is (= "Mr. Black" (:name account)))
        (is (= 0 (:balance account))))))
  
  (testing "find-account calls repository protocol correctly"
    (let [mock-repo (reify repo/AccountRepository
                      (create-account [_ _] nil)
                      (find-account [_ account-number]
                        (when (= account-number 1)
                          {:account-number 1 :name "Mr. Black" :balance 100}))
                      (save-account-event [_ _ _] nil))]
      (let [account (repo/find-account mock-repo 1)]
        (is (account/valid-account? account))
        (is (= 1 (:account-number account)))
        (is (= "Mr. Black" (:name account)))
        (is (= 100 (:balance account))))
      
      (let [account (repo/find-account mock-repo 999)]
        (is (nil? account)))))
  
  (testing "save-account-event calls repository protocol correctly"
    (let [save-spy (spy/spy (constantly {:event-id 1}))
          mock-repo (reify repo/AccountRepository
                      (create-account [_ _] nil)
                      (find-account [_ _] nil)
                      (save-account-event [_ account event]
                        (save-spy account event)))]
      (let [mock-account {:account-number 1 :name "Mr. Black" :balance 150}
            mock-event {:account-number 1 :event-type :deposit :event-data {:amount 50}}]
        (repo/save-account-event mock-repo mock-account mock-event)
        (spy-assert/called-once? save-spy)
        (spy-assert/called-with? save-spy mock-account mock-event)))))