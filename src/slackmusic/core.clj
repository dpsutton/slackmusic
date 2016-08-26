(ns slackmusic.core
  (:require [slackmusic.config :as config]
            [slackmusic.comms.slack-rtm :as rtm]
            [clojure.core.async :as async :refer [>! <! go go-loop]]
            [slackmusic.music-parser :as res])
  (:import java.lang.Thread)
  (:gen-class))

(defn -main [& args]
  (let [config (config/read-config)]
    (println ":: starting with config:" config)
    
    (go-loop [[in out stop] (rtm/start config)]
      (println ":: waiting for input")
      (if-let [form (<! in)]
        (let [input (:input form)
              response (-> input
                           res/title->link
                           res/polish
                           (#(filter (complement nil?) %)))]
          (println "trying to handle: " response)
          (println "want to send back to channel: " (get-in form [:meta :channel]))
          (>! out (assoc form :payload response))
          (recur [in out stop]))

        ;; something wrong happened, re init


        (do
          (println ":: WARNING! The comms went down, going to restart.")
          (stop)
          (<! (async/timeout 3000))
          (rtm/start config))))

    (.join (Thread/currentThread))))


