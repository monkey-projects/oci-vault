(ns user
  (:require [aero.core :as ac]
            [clojure.java.io :as io]
            [monkey.oci.common.utils :as u]
            [monkey.oci.vault :as v]))

(def conf (-> (io/resource "config.edn")
              (ac/read-config)
              (update :private-key u/load-privkey)))
