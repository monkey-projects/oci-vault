(ns monkey.oci.vault.mgmt-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian.test :as mt]
            [monkey.oci.vault
             [kms :as kms]
             [mgmt :as sut]]
            [monkey.oci.vault.test-helpers :refer [fake-conf]]))

(deftest make-mgmt-client
  (testing "fetches vault mgmt endpoint and returns a martian context"
    (with-redefs [kms/mgmt-endpoint (constantly "test-mgmt-ep")]
      (is (= "test-mgmt-ep/20180608"
             (-> fake-conf
                 (assoc :vault-id (str (random-uuid)))
                 sut/make-mgmt-client
                 :api-root)))))

  (testing "uses specified mgmt endpoint"
    (is (= "test-mgmt-ep/20180608"
           (-> fake-conf
               (assoc :mgmt-endpoint "test-mgmt-ep")
               sut/make-mgmt-client
               :api-root)))))

(deftest key-endpoints
  (let [mgmt-client (-> fake-conf
                        (assoc :mgmt-endpoint "http://mgmt")
                        (sut/make-mgmt-client))]
    (testing "can list crypto keys"
      (let [c (mt/respond-with mgmt-client
                               {:list-keys (constantly {:status 200})})]
        (is (= 200 (:status @(sut/list-keys c {:compartment-id "test-compartment-id"}))))))))
