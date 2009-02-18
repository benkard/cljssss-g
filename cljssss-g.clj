(ns cljssss-g
  (require [clojure.xml :as xml]
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup))
  (use compojure))

(def tgroup (new StringTemplateGroup ""))

(defservlet cljssss-g
  (GET "/"
    (.toString
     (doto (.getInstanceOf tgroup "index")
       (.setAttributes {"title" "Subscriptions"}))))
  (ANY "*"
    (page-not-found)))

(run-server {:port 8080}
  "/*" cljssss-g)
