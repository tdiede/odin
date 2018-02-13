(ns cards.admin.properties.views
  (:require [admin.properties.views :as property]
            [antizer.reagent :as ant]
            [cards.utils.seed :as seed]
            [devcards.core]
            [reagent.core :as r]
            [toolbelt.core :as tb])
  (:require-macros [devcards.core :as dc :refer [defcard
                                                 defcard-doc
                                                 defcard-rg]]))


(defcard-doc
  "
# Properties (a.k.a Communities)

Our properties interface should provide a way for our staff to manage all
aspects of a property; firstly, they'll need a way to quickly view a list of our
properties. There aren't many of them, and there's a lot of useful information
that can be conveyed quickly about them&mdash;thus I'd like to use a card-based
layout."
  )


(defcard-rg property-card
  "
Represents a single property for the list view. Accepts the following props:

- `:name`: Name of the property.
- `:href`: Link for the card's image & detail links.
- `:cover-image-url`: The image to render at the left side of the card.
- `:is-loading`: Whether or not the card is loading.
"
  (fn [data _]
    [:div

     [:div {:style {:margin-bottom 12}}
      [ant/button
       {:size     :small
        :on-click #(swap! data update :is-loading not)}
       "Toggle Loading"]]

     [:div.columns
      [:div.column.is-6.is-offset-3
       [property/property-card @data]]]])

  (r/atom {:name            "2072 Mission St."
           :cover-image-url "https://via.placeholder.com/128x96"
           :is-loading      false})

  {:inspect-data true})


(def sample-units
  (map
   (fn [i]
     (let [occupant (when (seed/heads?)
                      {:id   (seed/id)
                       :name (seed/full-name)
                       :ends (seed/future-date)})]
       (tb/assoc-when
        {:id    (seed/id)
         :name  (str "Unit #" (inc i))
         :floor (rand-nth [2 3])}
        :occupant occupant)))
   (range 20)))


(defcard-doc
  "
That's really it for the property overview. The interesting stuff is within
the detail view of a property.
")

(defcard-rg units-list
  "
In the unit-centric view, we'll want to be able to view a list of units from which to select.
"
  (fn [data _]
    [:div.columns
     [:div.column.is-6
      [property/units-list
       {:units     sample-units
        :page-size 15
        :active    (:active @data)
        :on-click #(swap! data assoc :active %)}]]])
  (r/atom {:active nil})
  {:inspect-data true})
