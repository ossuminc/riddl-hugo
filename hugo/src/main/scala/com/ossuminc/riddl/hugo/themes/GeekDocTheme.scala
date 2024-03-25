package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.MarkdownWriter

case class GeekDocTheme(mdw: MarkdownWriter) extends ThemeWriter(mdw) {
  def name: String = "GeekDoc"
}
