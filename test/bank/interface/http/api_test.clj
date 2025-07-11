(ns bank.interface.http.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [bank.interface.http.api :as api]))

(deftest api-specs-test
  (testing "create-account-request-spec validation"
    (is (true? (api/valid-create-account-request? {:name "John Doe"})))
    (is (false? (api/valid-create-account-request? {})))
    (is (false? (api/valid-create-account-request? {:name ""})))
    (is (false? (api/valid-create-account-request? {:name "John Doe" :extra "field"}))))

  (testing "create-account-response-spec validation"
    (is (true? (api/valid-create-account-response? {:account-number 1 :name "John Doe" :balance 0})))
    (is (false? (api/valid-create-account-response? {:name "John Doe" :balance 0})))
    (is (false? (api/valid-create-account-response? {:account-number 0 :name "John Doe" :balance 0})))
    (is (false? (api/valid-create-account-response? {:account-number 1 :name "" :balance 0})))
    (is (false? (api/valid-create-account-response? {:account-number 1 :name "John Doe" :balance -1}))))

  (testing "view-account-response-spec validation"
    (is (true? (api/valid-view-account-response? {:account-number 1 :name "John Doe" :balance 100})))
    (is (false? (api/valid-view-account-response? {:name "John Doe" :balance 100})))
    (is (false? (api/valid-view-account-response? {:account-number 0 :name "John Doe" :balance 100}))))

  (testing "deposit-request-spec validation"
    (is (true? (api/valid-deposit-request? {:amount 100})))
    (is (false? (api/valid-deposit-request? {:amount 0})))
    (is (false? (api/valid-deposit-request? {:amount -1})))
    (is (false? (api/valid-deposit-request? {})))
    (is (false? (api/valid-deposit-request? {:amount 100 :extra "field"}))))

  (testing "deposit-response-spec validation"
    (is (true? (api/valid-deposit-response? {:account-number 1 :name "John Doe" :balance 100})))
    (is (false? (api/valid-deposit-response? {:name "John Doe" :balance 100}))))

  (testing "error-response-spec validation"
    (is (true? (api/valid-error-response? {:error "not-found" :message "Account not found"})))
    (is (false? (api/valid-error-response? {:error "" :message "Account not found"})))
    (is (false? (api/valid-error-response? {:error "not-found" :message ""})))
    (is (false? (api/valid-error-response? {:message "Account not found"})))))

(deftest account-conversion-test
  (testing "account->response conversion"
    (let [saved-account {:id (random-uuid)
                        :account-number 123
                        :name "Jane Smith"
                        :balance 250}
          response (api/account->response saved-account)]
      (is (= {:account-number 123 :name "Jane Smith" :balance 250} response))
      (is (true? (api/valid-create-account-response? response)))
      (is (true? (api/valid-view-account-response? response)))
      (is (true? (api/valid-deposit-response? response))))))

(deftest generative-specs-test
  (testing "API specs can generate valid data"
    (let [create-req (mg/generate api/create-account-request-spec)
          create-resp (mg/generate api/create-account-response-spec)
          view-resp (mg/generate api/view-account-response-spec)
          deposit-req (mg/generate api/deposit-request-spec)
          deposit-resp (mg/generate api/deposit-response-spec)
          error-resp (mg/generate api/error-response-spec)]
      
      (is (true? (api/valid-create-account-request? create-req)))
      (is (true? (api/valid-create-account-response? create-resp)))
      (is (true? (api/valid-view-account-response? view-resp)))
      (is (true? (api/valid-deposit-request? deposit-req)))
      (is (true? (api/valid-deposit-response? deposit-resp)))
      (is (true? (api/valid-error-response? error-resp))))))
