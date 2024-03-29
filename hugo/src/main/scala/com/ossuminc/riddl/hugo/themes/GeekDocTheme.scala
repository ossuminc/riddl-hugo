package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.{MarkdownWriter, PassUtilities}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, Usages}
import com.ossuminc.riddl.passes.symbols.SymbolsOutput

import java.nio.file.Path

object GeekDocTheme {
  val name: String = "GeekDoc"
}

/** Theme extension to the MardownWriter for the Hugo GeekDoc theme */
case class GeekDocTheme(
  filePath: Path,
  commonOptions: CommonOptions,
  symbolsOutput: SymbolsOutput,
  refMap: ReferenceMap,
  usage: Usages,
  passUtilities: PassUtilities
) extends MarkdownWriter(filePath, commonOptions, symbolsOutput, refMap, usage, passUtilities) with ThemeWriter {
  
  def themeName: String = GeekDocTheme.name 
}
