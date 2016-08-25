(ns slackmusic.util)

(defn safe-resolve [kw]
  (let [user-ns (symbol (namespace kw))
        user-fn (symbol (name kw))]
    (println "Namespace: " user-ns " fn: " user-fn )
    (try
      (ns-resolve user-ns user-fn)
      (catch Throwable e
        (require user-ns)
        (ns-resolve user-ns user-fn)))))

(defn kw->fn [kw]
  (try
    (safe-resolve kw)
    (catch Throwable e
      (throw (ex-info "Could not resolve symbol on classpath" {:kw kw})))))
