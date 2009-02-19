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

(defservlet cljssss-g
  (GET "/"
    (.toString
     (doto (.getInstanceOf templates "index")
       (.setAttributes {"title" "Subscriptions",
                        "mainParagraph" "Hi there!"}))))
  (ANY "*"
    (page-not-found)))

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

(defn fetch-feed [id]
  (with-db
    (sql/with-query-results [{uri :uri}]
                            ["SELECT uri FROM feed WHERE id = ?" id]
      (let [feed #^SyndFeed (.build (new SyndFeedInput)
                                    (new XmlReader (new URL uri)))]
        (sql/transaction
         (sql/update-or-insert-values :feed
                                      ["id = ?" id]
                                      {:id id
                                       :uri uri
                                       :language (.getLanguage feed)
                                       :iri (.getURI feed)
                                       :link (.getLink feed)
                                       :rights (.trim (.getCopyright feed))
                                       :title (.trim (.getTitle feed))
                                       :subtitle (.trim (.getDescription feed))
                                       :updated (.getPublishedDate feed)})
         (doseq [entry #^SyndEntry (.getEntries feed)]
           (sql/with-query-results [{potential-entry-id :id}]
                                   ["SELECT id FROM entry WHERE iri = ?" (.getURI entry)]
            (let [entry-id
                  (or potential-entry-id
                      (+ 1
                         (sql/with-query-results max-id
                                                 ["SELECT MAX(id) FROM entry"]
                           (or max-id -1))))]
              (sql/update-or-insert-values :entry
                                           ["id = ?" entry-id]
                                           {:id entry-id
                                            :language (.getLanguage entry)
                                            :iri (.getURI entry)
                                            :link (.getLink entry)
                                            :rights (.trim (.getCopyright entry))
                                            :title (.trim (.getTitle entry))
                                            :summary_type (if (.getDescription entry)
                                                              (.getType (first (.getDescription entry)))
                                                              nil)
                                            :summary (if (.getDescription entry)
                                                         (.getValue (first (.getDescription entry)))
                                                         nil)
                                            :content_type (if (.getContents entry)
                                                              (.getType (first (.getContents entry)))
                                                              nil)
                                            :content (if (.getContents entry)
                                                         (.getValue (first (.getContents entry)))
                                                         nil)
                                            :updated (.getUpdatedDate entry)
                                            :published (.getPublishedDate entry)})
           (sql/update-or-insert-values :feed_entry_link
                                        ["feed = ?, entry = ?" id entry-id]
                                        {:feed id
                                         :entry entry-id})))))))))


(run-server {:port 8080}
  "/*" cljssss-g)


;;;; Sample database content
(comment
  (with-dbt
    (sql/update-or-insert-values :feed
                                 ["id = ?" 0]
                                 {:id 0
                                  :uri "http://matthias.benkard.de/journal/feed/"})
    (sql/update-or-insert-values :feed
                                 ["id = ?" 1]
                                 {:id 1
                                  :uri "http://uxul.wordpress.com/feed/"})))


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
                      [:updated "date"]))

  (with-dbt
    (sql/create-table :entry
                      [:id "integer" "PRIMARY KEY"]
                      [:uri "text"]  ;?
                      [:language "text"]
                      [:content "blob"]
                      [:content_type "text"]
                      [:iri "text"]
                      [:link "text"]
                      [:published "date"]
                      [:rights "text"]
                      [:source "integer"] ;:feed
                      [:title "text"]
                      [:summary "blob"]
                      [:summary_type "text"]
                      [:updated "date"]))

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
