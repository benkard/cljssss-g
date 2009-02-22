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

;; FIXME: WAAAAAAAAAH!
(def session {:id false})

(defmacro with-db [& body]
  `(sql/with-connection db-connection-data
     ~@body))

(defmacro with-dbt [& body]
  `(with-db
     (sql/transaction
       ~@body)))

;;; FIXME: clojure has no way of creating interfaces on-the-fly. So I
;;; cannot use stringtemplate here.
(defn opml-string [id]
  (with-dbt
    (sql/with-query-results
     results
     ["SELECT feed.uri, feed.link, user_feed_link.title FROM feed, user_feed_link WHERE user_feed_link.feed=feed.id AND user_feed_link.user=?" id]
     (str "<?xml version=\"1.0\" encoding=\"utf-8\"?><opml version=\"1.0\"><head>"
	  "<dateCreated>blah</dateCreated>"
	  "<title>G&ouml;del-Gentzen Clojure Syndication Services Super System Feed Export</title></head><body>"
      (loop [clstr "" r results]
	 (if (first r)
	     (recur
	      (str clstr
		   "<outline text=\""
		   (:title (first r))
		   "\" xmlUrl=\""
		   (:uri (first r))
		   "\" htmlUrl=\""
		   (:link (first r))
		   "\" />")
	      (rest r))
	     clstr))
      "</body></opml>"))))

(defn lynxy-feedlist [id]
  (with-dbt
    (sql/with-query-results
     results
     ["SELECT feed.id, feed.uri, feed.link, user_feed_link.title FROM feed, user_feed_link WHERE user_feed_link.feed=feed.id AND user_feed_link.user=?" id]
     (html `[html [head [title "G&ouml;del-Gentzen Clojure Syndication Services Super System"]]
	     [body [ul
	       ~@(loop [back '[] r results]
		   (if (first r)
		       (recur (concat back `[[li
					      [a {:href ~(str "lynxy-showfeed?feed=" (:id (first r)))}
						 ~(:title (first r)) " - "
						 ~(:link (first r))
						 ]]])
			      (rest r))
		       back))]]]))))

(defn lynxy-showfeed [id feednum]
  (with-dbt
    (sql/with-query-results
     results
     ["SELECT entry.link, entry.title FROM entry, feed_entry_link, user_feed_link WHERE entry.id = feed_entry_link.entry AND feed_entry_link.feed = user_feed_link.feed AND user_feed_link.user = ? AND user_feed_link.feed = ?" id feednum]
     (html `[html [head [title "G&ouml;del-Gentzen Clojure Syndication Services Super System"]]
	  [body [ul
	 ~@(loop [back '[] r results]
	      (if (first r)
		  (recur (concat back `[[li
			      [a {:href ~(:link (first r))}
				 ~(:title (first r)) " - "
				 ~(:link (first r))
				 ]]])
		      (rest r))
	       back))]
		[a {:href "lynxy-feedlist.html"} "&laquo; To Feedlist"]
		]]))))

(defmacro with-session [& body]
  `(if (not (session :id))
       (redirect-to "/login")
       (do ~@body)))
      

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
				    (params :name)]
				   (if (= password (params :password))
				       (do
					(alter session assoc :id id)
					(redirect-to "/"))
				       (redirect-to "/login?valuesofbetawillgiverisetodom=true"))))))
  (GET "/feedlist.opml"
       (with-session (opml-string (session :id))))
  (GET "/lynxy-feedlist.html"
       (with-session (lynxy-feedlist (session :id))))
  (GET "/lynxy-showfeed"
       (with-session
	 (lynxy-showfeed (session :id) (. Integer parseInt (params :feed)))))
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
