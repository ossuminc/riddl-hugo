package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.MarkdownWriter

trait ThemeWriter(mdw: MarkdownWriter) {
  
  def name: String
}
