(ns monkey.oci.vault.http
  "Common functions for http calls"
  (:require [monkey.oci.common.pagination :as p]))

(def json ["application/json"])

(defn api-route [opts]
  (assoc opts :produces json))

(defn paged-route [opts]
  (p/paged-route (api-route opts)))

(def api-version "20180608")
