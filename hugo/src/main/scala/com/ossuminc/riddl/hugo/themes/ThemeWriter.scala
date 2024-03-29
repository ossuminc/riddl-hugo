package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.MarkdownWriter

trait ThemeWriter { this: MarkdownWriter =>

  def themeName: String
}
