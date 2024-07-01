(ns monkey.oci.vault-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian.test :as mt]
            [monkey.oci.common.utils :as u]
            [monkey.oci.vault :as sut]))

(def fake-conf {:tenancy-ocid "test-tenancy"
                :user-ocid "test-user"
                :key-fingerprint "test-fingerprint"
                :private-key (u/generate-key)})

(deftest make-client
  (testing "creates martian context"
    (is (some? (:handlers (sut/make-client fake-conf))))))

(deftest vault-endpoints
  (testing "`get-vault` retrieves vault details"
    (let [c (-> (sut/make-client fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200})}))]
      (is (= 200 (:status @(sut/get-vault c {:vault-id "test-vault-id"}))))))

  (testing "`list-vaults` lists vaults in compartment"
    (let [c (-> (sut/make-client fake-conf)
                (mt/respond-with {:list-vaults (constantly {:status 200})}))]
      (is (= 200 (:status @(sut/list-vaults c {:compartment-id "test-compartment-id"})))))))

(deftest crypto-endpoint
  (testing "returns vault crypto endpoint property"
    (let [c (-> (sut/make-client fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200
                                                          :body {:crypto-endpoint "test-endpoint"}})}))]
      (is (= "test-endpoint" (sut/crypto-endpoint c {:vault-id "test-vault-id"}))))))

(deftest mgmt-endpoint
  (testing "returns vault mgmt endpoint property"
    (let [c (-> (sut/make-client fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200
                                                          :body {:management-endpoint "test-endpoint"}})}))]
      (is (= "test-endpoint" (sut/mgmt-endpoint c {:vault-id "test-vault-id"}))))))

(deftest make-crypto-client
  (testing "fetches vault crypto endpoint and returns a martian context"
    (with-redefs [sut/crypto-endpoint (constantly "test-crypto-ep")]
      (is (= "test-crypto-ep" (-> fake-conf
                                  (assoc :vault-id (str (random-uuid)))
                                  sut/make-crypto-client
                                  :api-root)))))

  (testing "uses specified crypto endpoint"
    (is (= "test-crypto-ep" (-> fake-conf
                                (assoc :crypto-endpoint "test-crypto-ep")
                                sut/make-crypto-client
                                :api-root)))))

(deftest encryption-endpoints
  (let [crypto-client (-> fake-conf
                          (assoc :crypto-endpoint "http://crypto")
                          (sut/make-crypto-client))]

    (testing "`encrypt` invokes encryption endpoint"
      (let [c (-> crypto-client
                  (mt/respond-with {:encrypt (constantly {:status 200})}))]
        (is (= 200 (:status @(sut/encrypt c {:encrypt {:key-id "test-key-id"
                                                       :plaintext "some text to encrypt"}}))))))

    (testing "`decrypt` invokes decryption endpoint"
      (let [c (-> crypto-client
                  (mt/respond-with {:decrypt (constantly {:status 200})}))]
        (is (= 200 (:status @(sut/decrypt c {:decrypt {:key-id "test-key-id"
                                                       :ciphertext "some text to decrypt"}}))))))))

(deftest make-mgmt-client
  (testing "fetches vault mgmt endpoint and returns a martian context"
    (with-redefs [sut/mgmt-endpoint (constantly "test-mgmt-ep")]
      (is (= "test-mgmt-ep" (-> fake-conf
                                  (assoc :vault-id (str (random-uuid)))
                                  sut/make-mgmt-client
                                  :api-root)))))

  (testing "uses specified mgmt endpoint"
    (is (= "test-mgmt-ep" (-> fake-conf
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
