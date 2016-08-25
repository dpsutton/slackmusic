(ns slackmusic.music-parser
  (:require [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]))

(defn title->spotify [title]
  (->> title
       (format "https://api.spotify.com/v1/search?q=%s&type=track&market=US&limit=1")
       (client/url-encode-illegal-characters)))

(defmulti title->link :type)
(defmethod title->link :default [data] data)
(defmethod title->link :command
  [{:keys [title] :as data}]
  (->> title
       title->spotify
       (assoc {:service :spotify :title title
               :origin :command} :url)))

(defmethod title->link :youtube-preview
  [data]
  (->> data
       :title
       title->spotify
       (assoc {:service :spotify :title (:title data)
               :origin :youtube-preview} :url)))

(defmulti get-data! :service)
(defmethod get-data! nil
  [{:keys [title type]}]
  (format "No info found for %s or I can't handle %s yet." title type))
(defmethod get-data! :spotify
  [{:keys [:url]}]
  (let [data (client/get url)]
    (when (client/success? data)
      (let [json (parse-string (:body data) true)]
        (-> json
            (get-in [:tracks :items])
            first
            (get-in [:external_urls :spotify]))))))
