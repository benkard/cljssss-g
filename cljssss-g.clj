(ns cljssss-g
  (require [net.cgrand.enlive-html :as enlive]
           [clojure.xml :as xml]
           compojure)
  (use compojure))

(defservlet cljssss-g
  (GET "/"
    (html [:html
           [:head
            [:title "G&ouml;del-Gentzen Clojure Syndication Services Super System"]]
           [:body
            [:h1 "G&ouml;del-Gentzen Clojure Syndication Services Super System"]
            [:p "Fnord."]]]))
  (ANY "*"
    (page-not-found)))

(run-server {:port 8080}
  "/*" cljssss-g)
