(ns monkey.oci.vault-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian
             [core :as mc]
             [test :as mt]]
            [monkey.oci.common.utils :as u]
            [monkey.oci.vault :as sut]
            [monkey.oci.vault
             [kms :as kms]
             [mgmt :as mgmt]]
            [monkey.oci.vault.test-helpers :as th]))

(deftest make-client
  (testing "creates common client object"
    (is (some? (sut/make-client th/fake-conf)))))

(deftest kms-routes
  (testing "`get-vault` exists"
    (is (fn? sut/get-vault)))

  (testing "`list-vaults` exists"
    (is (fn? sut/list-vaults)))

  (let [params {:compartment-id "test-compartment"
                :vault-id "test-vault"}]
    
    (testing "returns response body"
      (let [client {:kms (-> (kms/make-client th/fake-conf)
                             (mt/respond-with-constant {:get-vault {:status 200
                                                                    :body ::test-vault}}))}]
        (is (= ::test-vault (sut/get-vault client params)))))

    (testing "throws when source call fails"
      (let [client {:kms (-> (kms/make-client th/fake-conf)
                             (mt/respond-with-constant {:get-vault {:status 400}}))}]
        (is (thrown? Exception (sut/get-vault client params)))))))

(deftest mgmt-routes
  (testing "invokes target route if mgmt-endpoint specified in config"
    (let [client {:mgmt (atom {:client
                               (-> (mgmt/make-mgmt-client (assoc th/fake-conf :mgmt-endpoint "http://test"))
                                   (mt/respond-with-constant {:list-keys {:status 200
                                                                          :body ::test-result}}))})}]
      (is (= ::test-result (sut/list-keys client {:compartment-id "test-compartment"})))))

  (let [inv (atom 0)]
    (with-redefs [mgmt/make-mgmt-client (fn [conf]
                                          (swap! inv inc)
                                          (-> (mc/bootstrap "http://test-mgmt" mgmt/mgmt-routes)
                                              (mt/respond-with {:list-keys
                                                                (fn [req]
                                                                  (atom {:status 200
                                                                         :body (:url req)}))})))]
      (let [client (sut/make-client th/fake-conf)]
        (testing "fetches mgmt endpoint for vault if not specified in config"
          (is (= "http://test-mgmt/keys"
                 (sut/list-keys client {:compartment-id "test-compartment"
                                        :vault-id "test-vault"}))))

        (testing "fetches vault details only once"
          (is (some?(sut/list-keys client {:compartment-id "test-compartment"
                                           :vault-id "test-vault"})))
          (is (= 1 @inv)))))))

(deftest crypto-routes
  (testing "`encrypt` exists"
    (is (fn? sut/encrypt)))

  (testing "`decrypt` exists"
    (is (fn? sut/decrypt)))

  (testing "`generate-data-encryption-key` exists"
    (is (fn? sut/generate-data-encryption-key))))

(deftest secrets-routes
  (testing "`get-secret` exists"
    (is (fn? sut/get-secret)))

  (testing "`create-secret` exists"
    (is (fn? sut/create-secret)))

  (testing "`update-secret` exists"
    (is (fn? sut/update-secret))))

(deftest secret-retrieval-routes
  (testing "`get-secret-bundle` exists"
    (is (fn? sut/get-secret-bundle))))
