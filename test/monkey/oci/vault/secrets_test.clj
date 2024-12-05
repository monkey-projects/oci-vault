(ns monkey.oci.vault.secrets-test
  (:require [clojure.test :refer [deftest testing is]]
            [martian.test :as mt]
            [monkey.oci.vault
             [kms :as kms]
             [secrets :as sut]]
            [monkey.oci.vault.test-helpers :refer [fake-conf]]))

(deftest secret-endpoints
  (let [client (sut/make-secret-client fake-conf)]
    (testing "can create secret"
      (is (= 200 (-> client
                     (mt/respond-with-constant {:create-secret {:status 200}})
                     (sut/create-secret {:secret {:compartment-id "test-ocid"
                                                  :description "test secret"
                                                  :key-id "test-key"
                                                  :vault-id "test-vault"
                                                  :secret-name "test-secret"}})
                     deref
                     :status))))

    (testing "can update secret"
      (is (= 200 (-> client
                     (mt/respond-with-constant {:update-secret {:status 200}})
                     (sut/update-secret {:secret-id "test-secret"
                                         :secret {:description "test secret"}})
                     deref
                     :status))))

    (testing "can get secret"
      (is (= 200 (-> client
                     (mt/respond-with-constant {:get-secret {:status 200}})
                     (sut/get-secret {:secret-id "test-secret"})
                     deref
                     :status))))))

(deftest secret-retrieval-endpoints
  (let [client (sut/make-secret-retrieval-client fake-conf)]
    (testing "can get secret contents"
      (is (= 200 (-> client
                     (mt/respond-with-constant {:get-secret-bundle {:status 200}})
                     (sut/get-secret-bundle {:secret-id "test-secret"
                                             :stage "CURRENT"})
                     deref
                     :status))))))
