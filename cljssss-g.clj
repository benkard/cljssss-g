(ns cljssss-g
  (require [clojure.xml :as xml]
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup))
  (use compojure))

(def templates (new StringTemplateGroup ""))

(defservlet cljssss-g
  (GET "/"
    (.toString
     (doto (.getInstanceOf templates "index")
       (.setAttributes {"title" "Subscriptions",
                        "mainParagraph" "Hi there!"}))))
  (ANY "*"
    (page-not-found)))

(run-server {:port 8080}
  "/*" cljssss-g)
