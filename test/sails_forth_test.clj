(ns sails-forth-test
  (:require [clojure.core.typed :as t]
            [clojure.edn :as edn]
            [clojure.test :refer :all]
            [sails-forth :refer :all]))

(deftest test-types
  (t/check-ns 'sails-forth))

(defn load-config
  []
  (some-> (try (slurp "test/config.edn") (catch Exception _))
          edn/read-string))

(deftest ^:integration test-client
  (let [config (load-config)]
    (when-not config
      (throw (Exception. "Salesforce tests require a config")))
    (when-not (:sandbox? config)
      (throw (Exception. "Salesforce tests may only be run in a sandbox")))
    (testing "with valid credentials"
      (let [client (build-client! config)]
        (testing "can issue requests"
          (let [{:keys [status body]} (request! client :get "/limits" {})]
            (is (= 200 status))
            (is (map? body))))
        (testing "derefs to report its state"
          (let [state @client]
            (is (= 3 (:requests state)))
            (is (:authentication state))))
        (testing "caches authentication and version"
          (is (= 200 (:status (request! client :get "/limits" {}))))
          (is (= 4 (:requests @client))))))
    (testing "with invalid credentials"
      (let [config (update config :token str "x")
            client (build-client! config)]
        (testing "cannot issue requests"
          (is (nil? (request! client :get "/limits" {}))))
        (testing "is not authenticated"
          (let [state @client]
            (is (not (:authentication state)))
            (is (= 1 (:requests state)))))
        (testing "attempts to authenticate again"
          (request! client :get "/limits" {})
          (is (= 2 (:requests @client))))))
    (testing "with a read-only client"
      (let [config (assoc config :read-only? true)
            client (build-client! config)]
        (testing "cannot issue side effecting requests"
          (doseq [method [:post :put :patch :delete]]
            (is (thrown? Exception (request! client method "/anything" {})))))))))
