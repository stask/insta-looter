(ns insta-looter.core
  (:require
   [clojure.walk           :refer [keywordize-keys]]
   [clj-http.core          :as clj-http]
   [clj-http.client        :as client]
   [clj-http.cookies       :as cookies]
   [pl.danieljanus.tagsoup :as tagsoup]
   [cheshire.core          :as json])
  (:import
   (java.util Date)))

(def user-agent "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0")
(def headers {"User-Agent"                user-agent
              "Accept"                    "text/html"
              "DNT"                       "1"
              "Upgrade-Insecure-Requests" "1"})
(def ig-base-url "https://www.instagram.com/%s/")
(def shared-data-ptrn (re-pattern "window._sharedData = (\\{[^\\n]*\\});"))

(defn shared-data [html]
  (let [[_ _ _ [_ _ & tags]] html]
    (->> tags
         (filter #(= :script (first %)))
         first
         last
         (re-find shared-data-ptrn)
         last
         json/parse-string)))

(defn translate-media [node]
  {:id            (get node "id")
   :owner         (get-in node ["owner" "id"])
   :video?        (get node "is_video")
   :thumbnail-url (get node "thumbnail_src")
   :url           (get node "display_src")
   :dimensions    (keywordize-keys (get node "dimensions"))
   :comments      (get-in node ["comments" "count"])
   :date          (Date. (* (get node "date") 1000))
   :caption       (get node "caption")
   :code          (get node "code")
   :likes         (get-in node ["likes" "count"])})

(defn translate-profile [data n]
  (let [profile (get-in data ["entry_data" "ProfilePage" 0 "user"])]
    {:id             (get profile "id")
     :username       (get profile "username")
     :full-name      (get profile "full_name")
     :private?       (get profile "is_private")
     :verified?      (get profile "is_verified")
     :follows        (get-in profile ["follows" "count"])
     :followed-by    (get-in profile ["followed_by" "count"])
     :fb-page        (get profile "connected_fb_page")
     :profile-pic    (get profile "profile_pic_url")
     :profile-pic-hd (get profile "profile_pic_url_hd")
     :homepage       (get profile "external_url")
     :biography      (get profile "biography")
     :media          (map translate-media (get-in profile ["media" "nodes"]))}))

(defn loot-profile [username n]
  (let [profile-url (format ig-base-url username)]
    (binding [clj-http/*cookie-store* (cookies/cookie-store)]
      (let [{:keys [status headers body]} (client/get profile-url {:headers headers})]
        (if (= 200 status)
          (translate-profile (shared-data (tagsoup/parse-string body)) n)
          {:error {:status  status
                   :headers headers
                   :body    body}})))))
