(ns odin.utils.norms)


(defn make-refs
  "Create a list of references to `records` under `key`."
  [db norms-key records & {:keys [lookup-key refs-key]
                           :or   {lookup-key :id
                                  refs-key   :refs}}]
  (assoc-in db [norms-key refs-key] (map #(select-keys % [lookup-key]) records)))


(defn normalize
  "Normalize `records` under `key` by storing them in an associative
  structure (map) using `lookup-key` (defaults to `:id`)."
  [db norms-key records & {:keys [lookup-key refs-key]
                           :or   {lookup-key :id
                                  refs-key   :refs}}]
  (let [existing-norms (get-in db [norms-key :norms] {})
        merged-norms   (reduce
                        (fn [new-norms new-record]
                          (let [id         (get new-record lookup-key)
                                old-record (get new-norms id)]
                            (assoc new-norms id (merge old-record new-record))))
                        existing-norms
                        records)]
    (-> (assoc-in db [norms-key :norms] merged-norms)
        (make-refs norms-key records
                   :lookup-key lookup-key
                   :refs-key refs-key))))


(defn denormalize
  "Denormalize the refs found under `refs-key`."
  [db norms-key & {:keys [refs-key lookup-key]
                   :or   {lookup-key :id
                          refs-key   :refs}}]
  (let [refs (get-in db [norms-key refs-key])]
    (mapv #(let [v (get % lookup-key)]
             (get-in db [norms-key :norms v]))
          refs)))
