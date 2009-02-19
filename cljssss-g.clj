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
                         :subname     "cljssss-g.sqlite3"}
     (sql/transaction
       ~@body)))

(run-server {:port 8080}
  "/*" cljssss-g)
