(ns cljssss-g
  (require [clojure.xml :as xml]
           [clojure.contrib.sql :as sql]
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup)
          (com.sun.syndication.io SyndFeedInput XmlReader)
          (com.sun.syndication.feed.synd SyndFeed SyndEntry)
          (java.net URL))
  (use compojure))

(Class/forName "org.sqlite.JDBC")

(def templates (new StringTemplateGroup ""))

(def db-connection-data {:classname   "org.sqlite.JDBC"
                         :subprotocol "sqlite"
                         :subname     "cljssss-g.sqlite3"
                         :create      true})

(defmacro with-db [& body]
  `(sql/with-connection db-connection-data
     ~@body))

(defmacro with-dbt [& body]
  `(with-db
     (sql/transaction
       ~@body)))

(def web-vars '(context cookies headers method params request response
                route session))

(defmacro define-web-vars []
  `(do
     ~@(into () (map (fn [varname] `(def ~varname)) web-vars))))

(define-web-vars)

(defmacro with-web-vars [& body]
  `(binding ~(into [] (mapcat (fn [varname] `[~varname ~varname])
                              web-vars))
     ~@body))

(defn opml-string [user]
  (with-dbt
    (sql/with-query-results
         results
         [(str "SELECT feed.uri, feed.link, user_feed_link.title"
               " FROM feed, user_feed_link"
               " WHERE user_feed_link.feed=feed.id AND user_feed_link.user=?"
               " ORDER BY user_feed_link.title")
          user]
      (.toString (doto (.getInstanceOf templates "opml")
                   (.setAttributes {"date" ""  ;FIXME
                                    "feeds"
                                      (map (fn [{title :title
                                                 uri :uri
                                                 link :link}]
                                             {"text" title
                                              "xmlurl" uri
                                              "htmlurl" link})
                                           results)}))))))

(defn lynxy-feedlist [feed]
  (with-dbt
    (sql/with-query-results
         results
         [(str "SELECT feed.id, feed.uri, feed.link, user_feed_link.title"
               " FROM feed, user_feed_link"
               " WHERE user_feed_link.feed=feed.id AND user_feed_link.user=?"
               " ORDER BY user_feed_link.title")
          feed]
      (.toString (doto (.getInstanceOf templates "simple-feed-list")
                   (.setAttributes {"feeds"
                                      (map (fn [{title :title
                                                 id :id
                                                 link :link}]
                                             {"title" title
                                              "id" id
                                              "link" link})
                                           results)}))))))

(defn lynxy-showfeed [user feed]
  (with-dbt
    (sql/with-query-results [{feed-name :title}]
                            [(str "SELECT user_feed_link.title"
                                  " FROM feed, user_feed_link"
                                  " WHERE user_feed_link.feed = ?"
                                  "   AND user_feed_link.user = ?")
                             feed user]
      (sql/with-query-results
           results
           [(str "SELECT entry.link, entry.title"
                 " FROM entry, feed_entry_link, user_feed_link"
                 " WHERE entry.id = feed_entry_link.entry"
                 "   AND feed_entry_link.feed = user_feed_link.feed"
                 "   AND user_feed_link.user = ?"
                 "   AND user_feed_link.feed = ?"
                 " ORDER BY entry.published DESC")
            user feed]
       (.toString (doto (.getInstanceOf templates "simple-entry-list")
                    (.setAttributes {"feed_name" feed-name
                                     "entries"
                                       (map (fn [{title :title
                                                  link :link}]
                                              {"title" title
                                               "link" link})
                                            results)})))))))

(defmacro with-session
  "Rebind Compojure's magic lexical variables as vars."
  [& body]
  `(with-web-vars
     (if (not (session :id))
         (if (= (params :valuesofbetawillgiverisetodom) "true")
             (.toString (doto (.getInstanceOf templates "login")
                          (.setAttributes {"logintext" "Login failed"})))
             (.toString (doto (.getInstanceOf templates "login")
                          (.setAttributes {"logintext" "Login"}))))
         (do ~@body))))


(defservlet cljssss-g
  (POST "/login"
    (dosync
      (with-db
        (sql/with-query-results [{id :id password :password}]
                                ["SELECT id, password FROM user WHERE name = ?"
                                 (params :name)]
          (when (= password (params :password))
            (alter session assoc :id id))
          (redirect-to (or (headers :referer) "/"))))))
  (GET "/feedlist.opml"
    (with-session (opml-string (session :id))))
  (GET "/lynxy-feedlist.html"
    (with-session (lynxy-feedlist (session :id))))
  (GET "/lynxy-showfeed"
    (with-session
      (lynxy-showfeed (session :id) (Integer/parseInt (params :feed)))))
  (GET "/"
    (with-session
      (.toString
       (doto (.getInstanceOf templates "index")
         (.setAttributes {"title" "Subscriptions",
                          "mainParagraph" "Hi there!"})))))
  (ANY "*"
    (page-not-found)))

(defn trim-nil [thing]
  (and thing (.trim thing)))

(defn maximum-id [table-name]
  (sql/with-query-results [max-id-map]
                          [(str "SELECT MAX(id) FROM " table-name)]
    (or (second (first max-id-map)) -1)))

(defn fetch-feed [id]
  (with-db
    (let [uri (sql/with-query-results [{uri :uri}]
                                      ["SELECT uri FROM feed WHERE id = ?" id]
                uri),
          #^SyndFeed
          feed (.build (new SyndFeedInput)
                       (new XmlReader (new URL uri)))]
      (sql/transaction
       (sql/update-values :feed
                          ["id = ?" id]
                          {:language (.getLanguage feed)
                           :iri (.getUri feed)
                           :link (.getLink feed)
                           :rights (trim-nil (.getCopyright feed))
                           :title (trim-nil (.getTitle feed))
                           :subtitle (trim-nil (.getDescription feed))
                           :updated (.getPublishedDate feed)})
       (doseq [#^SyndEntry entry (.getEntries feed)]
         (sql/with-query-results [{potential-entry-id :id}]
                                 ["SELECT id FROM entry WHERE iri = ?" (.getUri entry)]
           (let [entry-id
                 (or potential-entry-id (+ 1 (maximum-id "entry")))]
             (sql/update-or-insert-values :entry
                                          ["id = ?" entry-id]
                                          {:id entry-id
                                           :iri (.getUri entry)
                                           :link (.getLink entry)
                                           :title (trim-nil (.getTitle entry))
                                           :summary_type (if (.getDescription entry)
                                                             (.getType (.getDescription entry))
                                                             nil)
                                           :summary (if (.getDescription entry)
                                                        (.getValue (.getDescription entry))
                                                        nil)
                                           :content_type (if (and (.getContents entry)
                                                                  (first (.getContents entry)))
                                                             (.getType (first (.getContents entry)))
                                                             nil)
                                           :content (if (and (.getContents entry)
                                                             (first (.getContents entry)))
                                                        (.getValue (first (.getContents entry)))
                                                        nil)
                                           :updated (.getUpdatedDate entry)
                                           :published (.getPublishedDate entry)})
             (sql/update-or-insert-values :feed_entry_link
                                          ["feed = ? AND entry = ?" id entry-id]
                                          {:feed id
                                           :entry entry-id}))))))))

;; system-wide subscription
(defn subscribe-to-feed [url]
  (let [maybe-id
        (with-dbt
          (when-not
              (sql/with-query-results [{id :id}]
                                      ["SELECT id FROM feed WHERE uri=?" url]
                id)
            (let [free-id (+ 1 (maximum-id "feed"))]
              (sql/insert-values :feed [:id :uri] [free-id url])
              free-id)))]
    (when maybe-id
      (fetch-feed maybe-id))
    maybe-id))


(run-server {:port 8080}
  "/*" cljssss-g)


;;;; Sample database content
(comment
 (subscribe-to-feed "http://matthias.benkard.de/journal/feed/")
 (subscribe-to-feed "http://uxul.wordpress.com/feed/")
 (with-dbt
   (sql/insert-values :user
                      [:id :name :password]
                      [0 "mulk" "klum"])
   (sql/insert-values :user_feed_link
                      [:user :feed :title]
                      [0 0 "Kompottkins Weisheiten"])
   (sql/insert-values :user_feed_link
                      [:user :feed :title]
                      [0 1 "Dijkstrabühl"])))

;;;; Database schema
(comment
  (with-dbt
    (sql/create-table :user
                      [:id "integer" "PRIMARY KEY"]
                      [:name "text"]
                      [:email "text"]
                      [:password "text"]))

  (with-dbt
    (sql/create-table :feed
                      [:id "integer" "PRIMARY KEY"]
                      [:uri "text"]
                      [:language "text"]
                      [:iri "text"]
                      [:icon "blob"]
                      [:link "text"]
                      [:logo "text"]
                      [:rights "text"]
                      [:title "text"]
                      [:subtitle "text"]
                      [:updated "timestamp"]))

  (with-dbt
    (sql/create-table :entry
                      [:id "integer" "PRIMARY KEY"]
                      [:uri "text"]  ;?
                      [:language "text"]
                      [:content "blob"]
                      [:content_type "text"]
                      [:iri "text"]
                      [:link "text"]
                      [:published "timestamp"]
                      [:rights "text"]
                      [:source "integer"] ;:feed
                      [:title "text"]
                      [:summary "blob"]
                      [:summary_type "text"]
                      [:updated "timestamp"]))

  (with-dbt
    (sql/create-table :feed_entry_link
                      [:feed "integer"]
                      [:entry "integer"]))

  (with-dbt
    (sql/create-table :user_feed_link   ;subscription
                      [:user "integer"]
                      [:feed "integer"]
                      [:title "text"]))

  (with-dbt
    (sql/create-table :user_entry_link
                      [:user "integer"]
                      [:entry "integer"]
                      [:read "boolean"]
                      [:marked "boolean"]
                      [:hidden "boolean"])))
