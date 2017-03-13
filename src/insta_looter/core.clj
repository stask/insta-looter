(ns insta-looter.core
  (:require
   [clojure.walk           :refer [keywordize-keys]]
   [clj-http.core          :as clj-http]
   [clj-http.client        :as client]
   [clj-http.cookies       :as cookies]
   [pl.danieljanus.tagsoup :as tagsoup]
   [cheshire.core          :as json])
  (:import
   (java.util Date)
   (java.net URLEncoder)))

(def user-agent "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0")
(def headers {"User-Agent"                user-agent
              "Accept"                    "text/html"
              "DNT"                       "1"
              "Upgrade-Insecure-Requests" "1"})
(def ig-base-url "https://www.instagram.com/%s/")
(def ig-post-url "https://www.instagram.com/p/%s/")
(def ig-search-url "https://www.instagram.com/web/search/topsearch/?context=blended&query=%s")
(def shared-data-ptrn (re-pattern "window._sharedData = (\\{[^\\n]*\\});"))

(defn url-encode [s]
  (-> s
      (URLEncoder/encode "UTF-8")
      (.replace "+" "%20")))

(defn shared-data [html]
  (let [[_ _ _ [_ _ & tags]] html]
    (->> tags
         (filter #(= :script (first %)))
         first
         last
         (re-find shared-data-ptrn)
         last
         json/parse-string)))

(defn parse-date [raw-date]
  (Date. (* raw-date 1000)))

(defn translate-comments [{:strs [count nodes]}]
  (cond-> {:count count}
    nodes (assoc :posts (map (fn [node]
                               {:text (get node "text")
                                :date (parse-date (get node "created_at"))
                                :user {:id       (get-in node ["user" "id"])
                                       :username (get-in node ["user" "username"])}})
                             nodes))))

(defn translate-media [node]
  {:id            (get node "id")
   :owner         (get-in node ["owner" "id"])
   :video?        (get node "is_video")
   :thumbnail-url (get node "thumbnail_src")
   :url           (get node "display_src")
   :dimensions    (keywordize-keys (get node "dimensions"))
   :comments      (translate-comments (get node "comments"))
   :date          (parse-date (get node "date"))
   :caption       (get node "caption")
   :code          (get node "code")
   :likes         (get-in node ["likes" "count"])})

(defn update-profile [profile data]
  (cond-> profile
    (empty? profile) (assoc :id             (get data "id")
                            :username       (get data "username")
                            :full-name      (get data "full_name")
                            :private?       (get data "is_private")
                            :verified?      (get data "is_verified")
                            :follows        (get-in data ["follows" "count"])
                            :followed-by    (get-in data ["followed_by" "count"])
                            :fb-page        (get data "connected_fb_page")
                            :profile-pic    (get data "profile_pic_url")
                            :profile-pic-hd (get data "profile_pic_url_hd")
                            :homepage       (get data "external_url")
                            :biography      (get data "biography")
                            :posts          (get-in data ["media" "count"]))
    :always          (update :media concat
                             (map translate-media (get-in data ["media" "nodes"])))))

(defn loot-profile [username n]
  (binding [clj-http/*cookie-store* (cookies/cookie-store)]
    (loop [profile {} url (format ig-base-url username)]
      (let [{:keys [status headers body]} (client/get url {:headers headers})]
        (if (= 200 status)
          (let [profile-page (-> body
                                 tagsoup/parse-string
                                 shared-data
                                 (get-in ["entry_data" "ProfilePage" 0 "user"]))
                has-more?    (get-in profile-page ["media" "page_info" "has_next_page"])
                profile      (update-profile profile profile-page)]
            (if (and has-more? (< (count (:media profile)) n))
              (recur profile (str (format ig-base-url username)
                                  "?max_id="
                                  (get-in profile-page ["media" "page_info" "end_cursor"])))
              profile))
          {:error   {:status  status
                     :headers headers
                     :body    body}
           :profile profile})))))

(defn loot-post [code]
  (let [url (format ig-post-url code)]
    (binding [clj-http/*cookie-store* (cookies/cookie-store)]
      (let [{:keys [status headers body]} (client/get url {:headers headers})]
        (if (= 200 status)
          (let [media-page (-> body
                               tagsoup/parse-string
                               shared-data
                               (get-in ["entry_data" "PostPage" 0 "media"]))
                profile    {:id          (get-in media-page ["owner" "id"])
                            :username    (get-in media-page ["owner" "username"])
                            :full-name   (get-in media-page ["owner" "full_name"])
                            :private?    (get-in media-page ["owner" "is_private"])
                            :profile-pic (get-in media-page ["owner" "profile_pic_url"])}
                post       (translate-media media-page)]
            {:profile profile
             :post    post})
          {:error {:status  status
                   :headers headers
                   :body    body}})))))

(defn search-users [query]
  (let [url                          (format ig-search-url (url-encode query))
        {:keys [status header body]} (client/get url {:headers headers})]
    (if (= 200 status)
      (map (fn [{:strs [user]}]
             {:username    (get user "username")
              :full-name   (get user "full_name")
              :verified?   (get user "is_verified")
              :private?    (get user "is_private")
              :followers   (get user "follower_count")
              :profile-pic (get user "profile_pic_url")})
           (-> body json/parse-string (get "users")))
      {:error {:status  status
               :headers headers
               :body    body}})))
