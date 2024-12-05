(ns monkey.oci.vault.crypto-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian.test :as mt]
            [monkey.oci.vault
             [crypto :as sut]
             [kms :as kms]]
            [monkey.oci.vault.test-helpers :refer [fake-conf]]))

(deftest make-crypto-client
  (testing "fetches vault crypto endpoint and returns a martian context"
    (with-redefs [kms/crypto-endpoint (constantly "test-crypto-ep")]
      (is (= "test-crypto-ep/20180608"
             (-> fake-conf
                 (assoc :vault-id (str (random-uuid)))
                 sut/make-crypto-client
                 :api-root)))))

  (testing "uses specified crypto endpoint"
    (is (= "test-crypto-ep/20180608"
           (-> fake-conf
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
                                                       :ciphertext "some text to decrypt"}}))))))

    (testing "`generate-data-encryption-key` invokes `POST`"
      (let [c (-> crypto-client
                  (mt/respond-with {:generate-data-encryption-key (constantly {:status 200})}))]
        (is (= 200 (:status @(sut/generate-data-encryption-key
                              c
                              {:key-details
                               {:key-id "test-key-id"
                                :include-plaintext-key true
                                :associated-data "test data"
                                :logging-context {"role" "server"}
                                :key-shape
                                {:algorithm "AES"
                                 :curve-id "NIST_P256"
                                 :length 512}}}))))))))
