(ns slackmusic.config
  (:require [clojure.edn :as end]))

(defn read-config []
  (let [path "config.edn"]
    (if path
      (end/read-string (slurp path))
      (throw (ex-info "no config.end file found" {:path "config.edn"})))))
