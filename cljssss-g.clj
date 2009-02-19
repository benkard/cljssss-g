(ns cljssss-g
  (require [clojure.xml :as xml]
           [clojure.contrib.sql :as sql]
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup))
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

(defmacro with-db [& body]
  `(sql/with-connection {:classname   "org.sqlite.JDBC"
                         :subprotocol "sqlite"
                         :subname     "cljssss-g.sqlite3"
                         :create      true}
     (sql/transaction
       ~@body)))

(run-server {:port 8080}
  "/*" cljssss-g)


;;;; Database schema
(comment
  (with-db
    (sql/create-table :user
                      [:id "integer" "PRIMARY KEY"]
                      [:name "text"]
                      [:email "text"]
                      [:password "text"]))

  (with-db
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

  (with-db
    (sql/create-table :entry
                      [:id "integer" "PRIMARY KEY"]
                      [:uri "text"]
                      [:language "text"]
                      [:content "blob"]
                      [:content_type "text"]
                      [:iri "text"]
                      [:link "text"]
                      [:published "date"]
                      [:rights "text"]
                      [:source "integer"] ;:feed
                      [:title "text"]
                      [:summary "text"]
                      [:updated "date"]))

  (with-db
    (sql/create-table :feed_entry_link
                      [:feed "integer"]
                      [:entry "integer"]))

  (with-db
    (sql/create-table :user_feed_link   ;subscription
                      [:user "integer"]
                      [:feed "integer"]
                      [:title "text"]))

  (with-db
    (sql/create-table :user_entry_link
                      [:user "integer"]
                      [:entry "integer"]
                      [:read "boolean"]
                      [:marked "boolean"]
                      [:hidden "boolean"])))
