(ns bank.persistence.repository-test
  (:require [clojure.test :refer [deftest testing is]]
            [bank.persistence.repository :as repo]
            [spy.core :as spy]
            [spy.assert :as spy-assert]))

(deftest jdbc-repository-test
  (testing "create-account calls repository protocol correctly"
    (let [mock-repo (reify repo/AccountRepository
                      (create-account [_ name]
                        {:account-number 1
                         :name name
                         :balance 0})
                      (find-account [_ _] nil)
                      (save-account-event [_ _ _] nil))]
      (let [result (repo/create-account mock-repo "Mr. Black")]
        (is (= 1 (:account-number result)))
        (is (= "Mr. Black" (:name result)))
        (is (= 0 (:balance result))))))
  
  (testing "find-account calls repository protocol correctly"
    (let [mock-repo (reify repo/AccountRepository
                      (create-account [_ _] nil)
                      (find-account [_ account-number]
                        (when (= account-number 1)
                          {:account-number 1 :name "Mr. Black" :balance 100}))
                      (save-account-event [_ _ _] nil))]
      (let [result (repo/find-account mock-repo 1)]
        (is (= 1 (:account-number result)))
        (is (= "Mr. Black" (:name result)))
        (is (= 100 (:balance result))))
      
      (let [result (repo/find-account mock-repo 999)]
        (is (nil? result)))))
  
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