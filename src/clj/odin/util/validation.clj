(ns odin.util.validation
  (:refer-clojure :exclude [float])
  (:require [bouncer.validators :as v]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn- extract-errors
  [errors-map acc]
  (reduce
   (fn [acc [k v]]
     (cond
       (sequential? v) (concat acc v)
       (map? v)        (extract-errors v acc)
       :otherwise      (throw (ex-info (str "Unexpected errors format! Expected sequential or map, got " (type v))
                                       {:offending-value v :key k}))))
   acc
   errors-map))


(defn errors
  "Extract errors from a bouncer error map."
  [[errors _]]
  (extract-errors errors []))


(defn valid?
  ([vresult]
   (valid? vresult identity))
  ([[errors result] tf]
   (if (nil? errors)
     (tf result)
     false)))


(def not-valid? (comp not valid?))


(defn result [vresult]
  (second vresult))


;; =============================================================================
;; Validators
;; =============================================================================


(v/defvalidator float
  {:default-message-format "%s must be a floating-point number"}
  [maybe-float]
  (float? maybe-float))


(v/defvalidator inst
  {:default-message-format "%s must be an instant"}
  [maybe-inst]
  (inst? maybe-inst))
