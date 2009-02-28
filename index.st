$header(title=title)$

<h1 class="title">$title$</h1>

<div id="feed-list">
  <h2>Feeds</h2>
 <ul>
  $feeds:{feed |
    <li><a href="/?feed=$feed.id$">$feed.title$</a></li>   
  }$
 </ul>
</div>

$if(entries)$
<div id="feed-content">
  <h2>Entries</h2>
  <ul>
  $entries:{entry |
    $if(entry.active_p)$
    $! FIXME: Show content inline if possible. !$
    <li><a id="entry-$entry.id$" href="/entries/$entry.id$?feed=$active_feed_id$#entry-$entry.id$">$entry.title$</a></li>
    $else$
    <li><a id="entry-$entry.id$" href="/entries/$entry.id$?feed=$active_feed_id$#entry-$entry.id$">$entry.title$</a></li>
    $endif$
   }$
  </ul>
</div>
$endif$

$footer()$

$!
Local Variables:
  mode: html
  coding: utf-8
End:
!$
