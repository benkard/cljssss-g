#  The Gödel-Gentzen Clojure Syndication Services Super System

CljSSSS<sup>g</sup> is a web-based feed reader written in Clojure.

## Dependencies

CljSSSS<sup>g</sup> requires the following items to be on the class path:

 - [Clojure](http://clojure.org/)
 - [Clojure Contrib](http://code.google.com/p/clojure-contrib/)
 - [Compojure](http://groups.google.com/group/compojure) along with all of its dependencies, including:
   * [Fact](http://github.com/weavejester/fact)
   * [Jetty](http://www.mortbay.org/)
   * [Rend](http://github.com/weavejester/rend)
   * The [Apache Commons Logging API](http://commons.apache.org/logging/)
   * The [Java Servlet API](http://java.sun.com/products/servlet/)
 - [StringTemplate](http://www.stringtemplate.org/) along with [ANTLR](http://www.antlr.org/)
 - [ROME](https://rome.dev.java.net/) along with [JDOM](http://www.jdom.org/)
 - [SQLiteJDBC](http://zentus.com/sqlitejdbc/) (if you want to deploy using SQLite)

## Deployment

In order to run CljSSSS<sup>g</sup>, load the main file, `cljssss-g.clj`.

You will probably want to populate your database with a couple of user records before trying to log in.  See the bottom of the `cljssss-g.clj` file for the database schema and examples.

<!--
Local Variables:
  mode: markdown
  coding: utf-8
End:
-->
