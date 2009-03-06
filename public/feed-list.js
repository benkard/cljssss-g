YUI({base: "/yui3/build/", timeout: 10000}).use("io-base", function(Y) {
  function successHandler(entry_id) {
    function updateEntry(id, response) {
      var elem = Y.Node.get("#entry-" + entry_id + "-container");
      var xml = response.responseXML.documentElement;
      var uri = xml.getElementsByTagName('uri')[0].firstChild.nodeValue;
      var title = xml.getElementsByTagName('title')[0].firstChild.nodeValue;
      var content = xml.getElementsByTagName('content')[0].firstChild.nodeValue;
      elem.set("innerHTML", '<a href="' + uri + '"><h3>' + title + '</h3></a>' +
                            '<div class="entry-content">' + content + '</div>');
    }

    return updateEntry;
  }

  function failureHandler(entry_id) {
    function updateEntry(id, response) {
      var elem = Y.Node.get("#entry-" + entry_id + "-container");
      // FIXME
      alert ("No content.");
    }

    return updateEntry;
  }

  function getEntry(event) {
    event.preventDefault();
    var id_string = this.get('id');
    var id = id_string.slice(6);
    var queryURI = '/entry-xml?id=' + id;
    Y.log("Querying server for entry: " + id, "info", "cljssss^g");
    var request = Y.io(queryURI, {
      method: "GET",
      on: {
        success: successHandler(id),
        failure: failureHandler(id)
      }});
  }

//  Y.on("click", getEntry, ".entry-link");
  Y.Node.all(".entry-link").each(function(elem, key) {
    Y.on("click", getEntry, elem, elem);
  });

});
