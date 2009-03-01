(ns cljssss-g
  (require [clojure.xml :as xml]
           [clojure.zip :as zip]
           [clojure.contrib.sql :as sql]
           clojure.contrib.zip-filter.xml
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup)
          (com.sun.syndication.io SyndFeedInput XmlReader)
          (com.sun.syndication.feed.synd SyndFeed SyndEntry)
          (java.net URL))
  (use compojure
       clojure.contrib.zip-filter.xml))

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

(defn select-feeds [user active-feed-id]
  (sql/with-query-results
       results
       [(str "SELECT feed.id, feed.uri, feed.link, user_feed_link.title"
             " FROM feed, user_feed_link"
             " WHERE user_feed_link.feed=feed.id AND user_feed_link.user=?"
             " ORDER BY user_feed_link.title")
        user]
    (doall (map (fn [{title :title
                      id :id
                      link :link}]
                  {"title" title
                   "id" id
                   "link" link
                   "active_p" (= active-feed-id id)})
                results))))

(defn select-feed-name [user feed-id]
  (sql/with-query-results [{feed-name :title}]
                          [(str "SELECT user_feed_link.title"
                                " FROM feed, user_feed_link"
                                " WHERE user_feed_link.feed = ?"
                                "   AND user_feed_link.user = ?")
                           feed-id user]
    feed-name))

(defn select-entries [user feed-id active-entry-id]
  (sql/with-query-results
       results
       [(str "SELECT entry.link, entry.title, entry.id"
             " FROM entry, feed_entry_link, user_feed_link"
             " WHERE entry.id = feed_entry_link.entry"
             "   AND feed_entry_link.feed = user_feed_link.feed"
             "   AND user_feed_link.user = ?"
             "   AND user_feed_link.feed = ?"
             " ORDER BY entry.published DESC")
        user feed-id]
    (doall (map (fn [{title :title
                      link :link
                      id :id}]
                  {"title" title
                   "link" link
                   "id" id
                   "active_p" (= active-entry-id id)})
                results))))

(defn lynxy-feedlist [user]
  (with-db
    (.toString (doto (.getInstanceOf templates "simple-feed-list")
                 (.setAttributes {"feeds" (select-feeds user nil)})))))

(defn lynxy-showfeed [user feed]
  (with-db
    (.toString (doto (.getInstanceOf templates "simple-entry-list")
                 (.setAttributes {"feed_name" (select-feed-name user feed)
                                  "entries" (select-entries user feed nil)})))))

(defn unescape [text] 
  ;; FIXME
  ;(StringEscapeUtils/unescapeHtml text)
  text)

(defn startparse-tagsoup [s ch]
  (doto (org.ccil.cowan.tagsoup.Parser.)
    (.setContentHandler ch)
    (.parse s)))

(defn make-string-input [string]
  (org.xml.sax.InputSource. (java.io.StringReader. string)))

(defn html->xhtml [html]
  (xml/parse (make-string-input html) startparse-tagsoup))

(defn text->xhtml [text]
  {:tag :div :attrs nil :content [{:tag :p :attrs nil :content [text]}]})

(defn safe-tag?
  "Is the given foreign element safe for inclusion in the output?"
  [xml]
  (#{;; Formatting
     :b :bdo :big :br :center :font :em :i :pre :s :small :span
     :strike :strong :sub :sup :tt :u :xmp
     ;; Semantic markup
     :abbr :acronym :cite :code :del :dir :ins :kbd :q :samp :var
     ;; Headings
     :h1 :h2 :h3 :h4 :h5 :h6
     ;; Hypertext
     :a
     ;; Text blocks
     :blockquote :div :p :ol :ul
     ;; Lists
     :dd :dfn :dl :dt :li :menu :optgroup :option
     ;; Tables
     :caption :col :colgroup :table :tbody :td
     ;; 
     :area :img :hr :map
     ;; Forms
     :button :fieldset :form :input :label :legend :textarea :tfoot
     :th :thead :tr
     ;; Disallowed
     ;;:address :applet :base :basefont :body :frame :frameset :head
     ;;:html :iframe :isindex :link :meta :noframes :noscript :object
     ;;:param :script :style :title
     }
   (xml :tag)))

(defn tag-to-kill?
  "Is the given element to be removed from the content completely (as opposed
to merely being replaced with a div element)?"
  [xml]
  (#{:applet :base :basefont :frame :frameset :head :iframe :isindex
     :link :meta :object :param :script :style :title}
   (xml :tag)))

(defn retag [xml new-tag-name]
  (assoc xml
    :tag new-tag-name
    :attrs nil))

(defn prepare-content
  [xml]
  "Make HTML content safe for displaying by removing suspicious content."
  ;; FIXME: Output a string rather than a tree.
  (let [tree (-> (zip/xml-zip xml)
                 (zip/edit retag :div))]
    (loop [loc tree]
      (if (zip/end? loc)
          (zip/root loc)
          (recur (let [node (zip/node loc)]
                   (zip/next
                    (cond (or (string? node) (safe-tag? node)) loc
                          (tag-to-kill? node) (zip/remove loc)
                          true (zip/edit loc retag :span)))))))))

(defn entry-xhtml-content [entry]
  (sql/with-query-results
       [{content :content, content-type :content_type}]
       [(str "SELECT content, content_type"  ;, content_source
             " FROM entry"
             " WHERE entry.id = ?")
        entry]
    (let [content-source nil]
      (cond (nil? content)
              nil
            (= content-type "xhtml")
              (prepare-content (xml/parse (make-string-input content)))
            (or (= content-type "html") (= content-type "text/html"))
              (prepare-content (html->xhtml (unescape content)))
            (or (= content-type "text") (nil? content-type))
              (prepare-content (text->xhtml content))
            true
              nil))))

(defn show-subscriptions [user feed active-entry-id]
  (with-db
    (.toString (doto (.getInstanceOf templates "index")
                 (.setAttributes {"feeds" (select-feeds user feed)
                                  "entries" (when feed (select-entries user
                                                                       feed
                                                                       active-entry-id))
                                  "active_feed_id" feed
                                  "active_feed_title" (and feed
                                                           (select-feed-name user feed))
                                  "title" "Subscriptions"
                                  "xhtml_content" (and active-entry-id
                                                       (entry-xhtml-content active-entry-id))})))))

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
    (with-session (show-subscriptions (session :id)
                                      (and (params :feed)
                                           (Integer/parseInt (params :feed)))
                                      nil)))
  (GET "/entries/*"
    (with-session (show-subscriptions (session :id)
                                      (and (params :feed)
                                           (Integer/parseInt (params :feed)))
                                      5)))  ;FIXME
  (GET "/layout.css"
    (serve-file "layout.css"))
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
                      [:content_source "text"]
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
