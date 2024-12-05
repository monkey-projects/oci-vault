(ns monkey.oci.vault.test-helpers
  (:require [monkey.oci.common.utils :as u]))

(def fake-conf {:tenancy-ocid "test-tenancy"
                :user-ocid "test-user"
                :key-fingerprint "test-fingerprint"
                :private-key (u/generate-key)})
