$header(title=feed_name)$

<ul>
$entries:{entry |
  <li><a href="$entry.link$">$entry.title$</a></li>
}$
</ul>

$footer()$

$!
Local Variables:
  mode: html
  coding: utf-8
End:
!$
