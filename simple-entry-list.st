$header(title=feed_name)$

<ul>
$entries:{entry |
  <li><a href="$entry.link$">$entry.title$</a></li>
}$
</ul>

$footer()$
