(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             [github :as gh]]))

[(p/deps-library)
 (gh/release-job {:dependencies ["publish"]})]
