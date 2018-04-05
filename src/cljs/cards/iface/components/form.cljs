(ns cards.iface.components.form
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]])
  (:require [devcards.core]
            [iface.components.form :as form]
            [reagent.core :as r]))


(defcard-rg timepicker
  "
## Default Timepicker
By default, the `time-picker` component allows selections from 9am to 5pm (exclusive) in 30 minute intervals.
<br>
<br>
The `:value` prop of the picker must be a [moment](https://momentjs.com/docs) &mdash; the `:on-change` handler will supply a moment on change.
"
  (fn [data _]
    [form/time-picker
     {:value     (:value @data)
      :on-change #(swap! data assoc :value %)
      :style     {:width "200px"}}])
  (r/atom {})
  {:inspect-data true})


(defcard-rg timepicker-interval
  "## Interval
The `time-picker` allows for custom intervals.
```clojure
{:interval 105}
```
"
  [form/time-picker
   {:interval 105
    :style    {:width "200px"}}]
  {}
  {:heading false})


(defcard-rg timepicker-start-end
  "## Start & End
The `time-picker` allows for custom `start` and `end` times, where `start` and `end` are hours from `0-23`.
```clojure
{:start 7.15
 :end   11.5}
```
"
  [form/time-picker
   {:start 7.15
    :end   11.5
    :style {:width "200px"}}]
  {}
  {:heading false})
