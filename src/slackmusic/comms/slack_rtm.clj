(ns slackmusic.comms.slack-rtm
  (:require [clojure.core.async :as async :refer [go go-loop]]
            [clj-http.client :as http]
            [gniazdo.core :as ws]
            [cheshire.core :refer [parse-string generate-string]]))


(def ^:private rtm-socket-url
  "https://slack.com/api/rtm.start")

(defn get-websocket-url [api-token]
  (let [response (-> (http/get rtm-socket-url
                               {:query-params {:token api-token
                                               :no_unreads true}
                                :as :json})
                     :body)]
    (when (:ok response)
      (:url response))))

(defn ping-pong [out-pipe next-id delay]
  (go-loop []
    (async/<! (async/timeout delay))
    (printf "Sending a ping request")
    (async/>! out-pipe {:id (next-id) :type :ping})
    (recur)))

(defn connect-socket [url next-id]
  (let [in (async/chan)
        out (async/chan)
        socket (ws/connect url
                           :on-receive
                           (fn [m]
                             (async/put! in (parse-string m true)))
                           :on-error
                           (fn [_]
                             (async/close! in)))
        heart-beat (ping-pong out next-id 7000)]
    (go-loop []
      (let [m (async/<! out)
            s (generate-string m)]
        (ws/send-msg socket s)
        (recur)))
    [in out]))

(defn- youtube-unfurled?
  [data]
  (if-let [attach (get-in data [:message :attachments])]
    (when-let [title (and (vector? attach)
                          (= (:service_name (first attach)) "YouTube")
                     (-> attach first :title))]
      {:type :youtube-preview
       :title title})))
(defn- command?
  [prefix {:keys [text] :as data}]
  (when (and (string? text)
             (.startsWith text prefix))
    {:type :command :title (.substring text (count prefix))}))

(defn- can-handle? [prefix data]
  (or (youtube-unfurled? data)
      (command? prefix data)))

(defn start [{:keys [api-token prefix]}]
  (let [cin (async/chan 10)
        cout (async/chan 10)
        url (get-websocket-url api-token)
        counter (atom 0)
        next-id (fn []
                  (swap! counter inc))
        shutdown (fn []
                   (async/close! cin)
                   (async/close! cout))]
    (when (clojure.string/blank? url)
      (throw (ex-info "Could not get RTM Websocket URL" {})))

    (println ":: got websocket url:" url)

    ;; start a loop to process messages
    (go-loop [[in out] (connect-socket url next-id)]
      ;; get whatever needs to be done for either data coming from the socket
      ;; or from the user
      (let [[v p] (async/alts! [cout in])]
        ;; if something goes wrong, just die for now
        ;; we should do something smarter, may be try and reconnect
        (if (nil? v)
          (do
            (println "A channel returned nil, may be its dead? Leaving loop.")
            (shutdown))
          (do
            (if (= p cout)
              ;; the user sent us something, time to send it to the remote end point
              (doseq [link (:payload v)]
                (println link)
                (async/>! out (merge {:id (next-id) :type "message"
                                      :channel (get-in v [:meta :channel])}
                                     link)))

              ;; the websocket has sent us something, figure out if its of interest
              ;; to us, and if it is, send it to the evaluator
              (do
                (println ":: incoming:" v)
                (when-let [title-and-type (can-handle? prefix v)]
                  (println "can handle")
                  (println title-and-type)
                  (async/>! cin {:input title-and-type
                                 :meta v}))))
            (recur [in out])))))
    [cin cout shutdown]))
