(ns monkey.oci.vault.b64-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.oci.vault.b64 :as sut]))

(deftest base64
  (testing "to and from base64"
    (let [in "test string"
          b64 (sut/->b64 in)]
      (is (string? b64))
      (is (= in (String. (sut/b64-> b64)))))))
