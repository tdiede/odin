(ns odin.graphql.resolvers.kami)


;; =============================================================================
;; Fields
;; =============================================================================


(defn historic-code
  [_ _ building]
  :A)


(defn square-feet
  [_ _ building]
  0)


;; =============================================================================
;; Queries
;; =============================================================================


(defn search-buildings
  [ctx {query :query} _]
  )


(defn fetch-building
  [ctx {address :address} _]
  )


;; =============================================================================
;; Resolvers
;; =============================================================================


(def resolvers
  {:building/historic-code historic-code
   :building/sqft          square-feet

   :search-buildings search-buildings
   :fetch-building   fetch-building})
