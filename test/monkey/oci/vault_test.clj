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
      (is (= 200 (:status @(sut/get-vault c {:vault-id "test-vault-id"})))))))

(deftest crypto-endpoint
  (testing "returns vault crypto endpoint property"
    (let [c (-> (sut/make-client fake-conf)
                (mt/respond-with {:get-vault (constantly {:status 200
                                                          :body {:crypto-endpoint "test-endpoint"}})}))]
      (is (= "test-endpoint" (sut/crypto-endpoint c {:vault-id "test-vault-id"}))))))

(deftest make-crypto-client
  (testing "fetches vault crypto endpoint and returns a martian context"
    (with-redefs [sut/crypto-endpoint (constantly "test-crypto-ep")]
      (is (= "test-crypto-ep" (-> fake-conf
                                  (assoc :vault-id (str (random-uuid)))
                                  sut/make-crypto-client
                                  :api-root))))))

(deftest encryption-endpoints
  (with-redefs [sut/crypto-endpoint (constantly "test-crypto-ep")]
    
    (testing "`encrypt` invokes encryption endpoint"
      (let [c (-> (sut/make-crypto-client fake-conf)
                  (mt/respond-with {:encrypt (constantly {:status 200})}))]
        (is (= 200 (:status @(sut/encrypt c {:encrypt {:key-id "test-key-id"
                                                       :plaintext "some text to encrypt"}}))))))

    (testing "`decrypt` invokes decryption endpoint"
      (let [c (-> (sut/make-crypto-client fake-conf)
                  (mt/respond-with {:decrypt (constantly {:status 200})}))]
        (is (= 200 (:status @(sut/decrypt c {:decrypt {:key-id "test-key-id"
                                                       :ciphertext "some text to decrypt"}}))))))))
