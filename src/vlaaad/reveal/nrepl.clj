(ns vlaaad.reveal.nrepl
  (:require [nrepl.middleware :as middleware]
            [nrepl.middleware.print :as print]
            [nrepl.transport :as transport]
            [clojure.string :as str]
            [vlaaad.reveal.ui :as ui]
            [vlaaad.reveal.style :as style]
            [vlaaad.reveal.stream :as stream]))

(defn- show-output [ui request message]
  (when-let [code (:code request)]
    (when-not (or (str/starts-with? code "(cursive.repl.runtime/")
                  (:done (:status message))
                  (= 0 (:pprint request)))
      (let [{:keys [out value err nrepl.middleware.caught/throwable]
             :or {out ::not-found
                  value ::not-found
                  err ::not-found
                  throwable ::not-found}} message]
        (ui
          (cond
            (not= value ::not-found)
            (stream/as {:request request :message message}
              (stream/vertical
                (stream/raw-string code {:fill style/util-color})
                (stream/horizontal
                  (stream/raw-string "=>" {:fill style/util-color})
                  stream/separator
                  (stream/stream value))))

            (not= out ::not-found)
            (stream/as {:request request :message message}
              (stream/system-out (str/trim-newline out)))

            (not= err ::not-found)
            (stream/as {:request request :message message}
              (stream/system-err (str/trim-newline err)))

            (not= throwable ::not-found)
            (stream/as {:request request :message message}
              (stream/vertical
                (stream/raw-string code {:fill style/util-color})
                (stream/horizontal
                  (stream/raw-string "=>" {:fill style/util-color})
                  stream/separator
                  (stream/as throwable
                    (stream/raw-string
                      (.getSimpleName (class throwable))
                      {:fill style/error-color})))))

            :else
            {:request request :message message}))))))

(defn- show-tap [ui value]
  (ui
    (stream/as {:tap value}
      (stream/horizontal
        (stream/raw-string "tap>" {:fill style/util-color})
        stream/separator
        (stream/stream value)))))

(defn middleware [f]
  (let [ui (ui/make)]
    (add-tap #(show-tap ui %))
    (fn [request]
      (-> request
          (update :transport (fn [t]
                               (reify transport/Transport
                                 (recv [_] (transport/recv t))
                                 (recv [_ timeout]
                                   (transport/recv t timeout))
                                 (send [this message]
                                   (show-output ui request message)
                                   (transport/send t message)
                                   this))))
          (f)))))

(middleware/set-descriptor! #'middleware
                            {:requires #{#'print/wrap-print}
                             :expects #{"eval"}
                             :handles {}})

