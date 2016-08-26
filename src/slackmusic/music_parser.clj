(ns slackmusic.music-parser
  (:require [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]))

(defn title->spotify [{:keys [title] :as data}]
  (->> title
       (format "https://api.spotify.com/v1/search?q=%s&type=track&market=US&limit=1")
       (client/url-encode-illegal-characters)
       (assoc data :service :spotify :url )))

(defn title->gplay [{:keys [title] :as data}]
  (->> title
       (clojure.string/trim)
       (#(clojure.string/replace % " " "+"))
       (#(format "https://play.google.com/music/listen?u=0#/sr/%s" %))
       (assoc data :service :gplay :url)))

(def services #{title->spotify title->gplay})

(defmulti title->link :type)
;; don't do anything different based on the type at the moment
(defmethod title->link :default [data]
  (map #(% data) services))

(defmulti get-data! :service)

;; will always return
(defmethod get-data! :gplay
  [{:keys [title url]}]
  {:text url})

(comment
  {:attachments [{:title title
                  :pretext "Google Play Search Results"
                  :text (format "<%s>" url)
                  :mrkdwn_in [:text :pretext]
                  :fallback url}]})

(defmethod get-data! nil
  [{:keys [title type]}]
  (format "No info found for %s or I can't handle %s yet." title type))

;; not guaranteed to return anything
(defmethod get-data! :spotify
  [{:keys [url]}]
  (let [data (client/get url)]
    (when (client/success? data)
      (let [json (parse-string (:body data) true)
            spotify-url(-> json
                           (get-in [:tracks :items])
                           first
                           (get-in [:external_urls :spotify]))]
        (when spotify-url
          {:text spotify-url})))))

(defn polish
  [ms]
  (map get-data! ms))
