$header(title="Feed list")$

<ul>
$feeds:{feed |
  <li><a href="lynxy-showfeed?feed=$feed.id$">$feed.title$ &mdash; $feed.link$</a></li>
}$
</ul>

$footer()$

$!
Local Variables:
  mode: html
  coding: utf-8
End:
!$
