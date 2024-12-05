(ns monkey.oci.vault.kms-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian.test :as mt]
            [monkey.oci.vault.kms :as sut]
            [monkey.oci.vault.test-helpers :as th]))

(deftest make-client
  (testing "creates martian context"
    (is (some? (:handlers (sut/make-client th/fake-conf))))))

(deftest kms-endpoints
  (testing "`get-vault` retrieves vault details"
    (let [c (-> (sut/make-client th/fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200})}))]
      (is (= 200 (:status @(sut/get-vault c {:vault-id "test-vault-id"}))))))

  (testing "`list-vaults` lists vaults in compartment"
    (let [c (-> (sut/make-client th/fake-conf)
                (mt/respond-with {:list-vaults (constantly {:status 200})}))]
      (is (= 200 (:status @(sut/list-vaults c {:compartment-id "test-compartment-id"})))))))

(deftest crypto-endpoint
  (testing "returns vault crypto endpoint property"
    (let [c (-> (sut/make-client th/fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200
                                                          :body {:crypto-endpoint "test-endpoint"}})}))]
      (is (= "test-endpoint" (sut/crypto-endpoint c {:vault-id "test-vault-id"}))))))

(deftest mgmt-endpoint
  (testing "returns vault mgmt endpoint property"
    (let [c (-> (sut/make-client th/fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200
                                                          :body {:management-endpoint "test-endpoint"}})}))]
      (is (= "test-endpoint" (sut/mgmt-endpoint c {:vault-id "test-vault-id"}))))))
