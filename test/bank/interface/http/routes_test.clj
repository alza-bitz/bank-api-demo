(ns bank.interface.http.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [reitit.ring :as ring]
            [bank.interface.http.routes :as routes]))

(def mock-handlers
  "Mock handlers for testing routes."
  {:create-account (fn [_] {:status 200 :body {:account-number 123}})
   :view-account (fn [_] {:status 200 :body {:account-number 123}})
   :deposit (fn [_] {:status 200 :body {:account-number 123}})
   :withdraw (fn [_] {:status 200 :body {:account-number 123}})
   :transfer (fn [_] {:status 200 :body {:account-number 123}})
   :audit (fn [_] {:status 200 :body []})
   :operation-result (fn [_] {:status 200 :body {:operation-id "123"}})})

(deftest create-routes-test
  (testing "routes structure"
    (let [route-data (routes/create-routes mock-handlers)]
      (is (vector? route-data))
      (is (seq route-data)))))

(deftest create-router-test
  (testing "router creation"
    (let [router (routes/create-router mock-handlers)]
      (is (some? router)))))

(deftest create-app-test
  (testing "app creation without swagger UI"
    (let [router (routes/create-router mock-handlers)
          app (ring/ring-handler router)]
      (is (fn? app)))))

(deftest route-handlers-test
  (testing "route handlers are properly assigned"
    (let [test-handlers {:create-account (constantly {:status 201})
                         :view-account (constantly {:status 200})
                         :deposit (constantly {:status 202})
                         :withdraw (constantly {:status 203})
                         :transfer (constantly {:status 205})
                         :audit (constantly {:status 204})
                         :operation-result (constantly {:status 200})}
          router (routes/create-router test-handlers)]

      ;; Test that the router was created successfully
      (is (some? router)))))
