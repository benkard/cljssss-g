$header(title=title)$

<h1 class="title">$title$</h1>

<div id="feed-list-container">
<div id="feed-list">
  <h2>Feeds</h2>
  <ul>
  $feeds:{feed |
    <li><a href="/?feed=$feed.id$"
    $if(feed.active_p)$
           class="active-feed-name"
    $else$
           class="inactive-feed-name"
    $endif$
           >$feed.title$</a></li>
  }$
  </ul>
</div>
</div>

$if(entries)$
<div id="feed-content-container">
<div id="feed-content">
  <h2>$active_feed_title$</h2>
  <ul>
  $entries:{entry |
    $if(entry.active_p)$
    $! FIXME: Show content inline if possible. !$
    <li class="full-entry">
      <a id="entry-$entry.id$" href="$entry.link$"><h3>$entry.title$</h3></a>
      <div class="entry-content">$xhtml_content$</div>
    </li>
    $else$
    <li class="partial-entry">
      <a id="entry-$entry.id$" href="/entries/$entry.id$?feed=$active_feed_id$#entry-$entry.id$">$entry.title$</a>
    </li>
    $endif$
  }$
  </ul>
</div>
</div>
$endif$

$footer()$

$!
Local Variables:
  mode: html
  coding: utf-8
End:
!$
