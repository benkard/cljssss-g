(ns cljssss-g
  (require [clojure.xml :as xml]
           [clojure.contrib.sql :as sql]
           compojure)
  (import (org.antlr.stringtemplate StringTemplateGroup)
          (com.sun.syndication.io SyndFeedInput XmlReader)
          (com.sun.syndication.feed.synd SyndFeed SyndEntry)
          (java.net URL))
  (use compojure))


(gen-interface
 :name opmliface
 :prefix "opml-"
 :init init
 :methods [[getXmlurl [] String]
	   [getHtmlurl [] String]
	   [getText [] String]])