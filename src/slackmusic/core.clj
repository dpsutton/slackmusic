(ns slackmusic.core
  (:require [slackmusic.config :as config]
            [slackmusic.util :as util]
            [slackmusic.comms.slack-rtm :as rtm]
            [clojure.core.async :as async :refer [>! <! go go-loop]]
            [slackmusic.music-parser :as res])
  (:import java.lang.Thread)
  (:gen-class))

(def kill-fn (fn [] (println "kill function not hooked up")))

(defn make-comm [id config]
  (let [f (util/kw->fn id)]
    (f config)))

(defn -main [& args]
  (let [config (config/read-config)
        inst-comm (fn []
                    (println ":: building com:" (:comm config))
                    rtm/start)]
    (println ":: starting with config:" config)
    
    (go-loop [[in out stop] (rtm/start config)]
      (println ":: waiting for input")
      (if-let [form (<! in)]
        (let [input (:input form)
              response (-> input
                           res/title->link
                           res/get-data!)]
          (println "trying to handle: " response)
          (println "want to send back to channel: " (get-in form [:meta :channel]))
          (>! out (assoc form :musiclink (or response
                                             (format "couldn't match input %s" input))))
          (recur [in out stop]))

        ;; something wrong happened, re init


        (do
          (println ":: WARNING! The comms went down, going to restart.")
          (stop)
          (<! (async/timeout 3000))
          (inst-comm))))

    (.join (Thread/currentThread))))


