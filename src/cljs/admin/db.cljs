(ns admin.db)


(def menu-items
  [{:menu/key :home
    :menu/uri "/"}
   {:menu/key :accounts}
   {:menu/key :properties}
   {:menu/key :services}
   {:menu/key        :log-out
    :menu/uri        "/logout"
    :menu/text       "Log Out"
    :menu.ui/excluded #{:side}}])


(def default-value
  {:menu {:showing false
          :items   menu-items}})
