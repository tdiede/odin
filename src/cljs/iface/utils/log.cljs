(ns iface.utils.log)

(def log
  (.bind js/console.log js/console))


(def warn
  (.bind js/console.warn js/console))


(def error
  (.bind js/console.error js/console))
