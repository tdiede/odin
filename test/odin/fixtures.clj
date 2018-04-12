;; (ns odin.fixtures
;;   (:require [mock.data :as mock]
;;             [odin.core :as odin]
;;             [toolbelt.core :as tb]))

;; ;; ==============================================================================
;; ;; helpers ======================================================================
;; ;; ==============================================================================


;; (defn random-id
;;   "Generate a random identifier."
;;   []
;;   (str (java.util.UUID/randomUUID)))


;; (def ids
;;   "Produces an infinite sequence of random ids."
;;   (repeatedly random-id))


;; ;; ==============================================================================
;; ;; system =======================================================================
;; ;; ==============================================================================


;; (def ^:dynamic *system* nil)


;; #_(defn acquire-conn []
;;   (let [dbc    (teller/datomic-connection (str "datomic:mem://" (gensym)) :db.part/user)
;;         pyc    (teller/stripe-connection "sk_test_mPUtCMOnGXJwD6RAWMPou8PH")
;;         system (teller/system dbc pyc)]
;;     (teller/connect system)))


;; #_(defn release-conn
;;   [conn]
;;   (teller/disconnect conn))


;; (defmacro with-system
;;   "Acquires a datomic connection and binds it locally to symbol while executing
;;   body. Ensures resource is released after body completes. If called in a
;;   dynamic context in which *resource* is already bound, reuses the existing
;;   resource and does not release it."
;;   [symbol & body]
;;   `(let [~symbol (or *system* (acquire-conn))]
;;      (try ~@body
;;           (finally
;;             (when-not *system*
;;               (release-conn ~symbol))))))


;; #_(defn system
;;   "A fixture that creates a system in the context of `test-fn`."
;;   [test-fn]
;;   (with-system s
;;     (binding [*system* s]
;;       (test-fn))))


;; ;; ==============================================================================
;; ;; entity fixtures ==============================================================
;; ;; ==============================================================================


;; ;; accessors ====================================================================



;; ;; fixtures =====================================================================



;; ;; ==============================================================================
;; ;; scratch ======================================================================
;; ;; ==============================================================================


;; (comment

;;   (defn customer-fixture
;;     [& args]
;;     [(fn [] (with-system s (customer/by-id s (first args))))
;;      (apply fixtures/customer args)])


;;   (let [$customer (random-id)
;;         $plan     (random-id)]
;;     (let [[lookup1 fix1] (customer-fixture $customer :source visa-credit)
;;           [lookup2 fix2] (plan-fixture $plan :payment.type/order)]
;;       (with-fixtures [fix1 fix2]
;;         (let [$customer (lookup1)
;;               $plan     (lookup2)]
;;           (with-system s
;;             (testing "Cannot subscribe customer to a plan"
;;               (is (thrown? java.lang.AssertionError #"isn't affiliated with a property"
;;                            (sut/subscribe! $customer $plan {:source visa-credit}))
;;                   "the customer must be affiliated with a property")))))))


;;   (defmacro with-environment [specs & body]
;;     (let [specs    (partition 2 2 nil specs)
;;           syms     (map first specs)
;;           fixtures (map (fn [[sym fixp]]
;;                           (concat (list (first fixp) sym) (rest fixp)))
;;                         specs)]
;;       `(let [~@(mapcat (fn [sym] [sym `(fixtures/random-id)]) syms)]
;;          (with-fixtures [~@fixtures]
;;            ~@body))))


;;   (with-environment [$customer (fixtures/customer :source visa-credit)]
;;     (with-system s
;;       (customer/by-id s $customer)))


;;   ;; the dream
;;   (deftest subscribe-without-property
;;     (with-environment [$customer (fixtures/customer :source visa-credit)
;;                        $plan (fixtures/plan :payment.type/order)]
;;       (testing "Cannot subscribe customer to a plan"
;;         (is (thrown? java.lang.AssertionError #"isn't affiliated with a property"
;;                      (sut/subscribe! $customer $plan {:source visa-credit}))
;;             "the customer must be affiliated with a property"))))
;;   )
