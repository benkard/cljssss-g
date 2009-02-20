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

(defn login-view []
  (.toString (.getInstanceOf templates "login")))


(defservlet cljssss-g
  (GET "/login"
       (if (= (params :valuesofbetawillgiverisetodom) "true")
	   (.toString (doto
		       (.getInstanceOf templates "login")
		       (.setAttributes {"logintext" "Login failed"})))
	   (.toString (doto
		       (.getInstanceOf templates "login")
		       (.setAttributes {"logintext" "Login"})))))
  (POST "/login"
	(dosync
     (with-db
	  (sql/with-query-results [{id :id password :password}]
                                ["SELECT id, password FROM user WHERE name = ?"
				(@params :name)]
				 (if (= password (@params :password))
              (do
                (alter session assoc :id id)
                (redirect-to "/"))
              (redirect-to "/login?valuesofbetawillgiverisetodom=true"))))))
  (GET "/"
    (if (@session :id)
        (.toString
         (doto (.getInstanceOf templates "index")
           (.setAttributes {"title" "Subscriptions",
                            "mainParagraph" "Hi there!"})))
        (redirect-to "/login")))
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
          feed #^SyndFeed (.build (new SyndFeedInput)
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
       (doseq [entry #^SyndEntry (.getEntries feed)]
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
 (subscribe-to-feed "http://uxul.wordpress.com/feed/"))

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
