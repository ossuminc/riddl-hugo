package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.{MarkdownWriter, PassUtilities}
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, Usages}
import com.ossuminc.riddl.passes.symbols.SymbolsOutput

import java.nio.file.Path

object DotdockTheme {
  val name = "DotDock"
}

/** Theme extensions to the Markdown writer for the Dotdock Hugo theme
  *
  * @param filePath
  *   Path to the file being written
  * @param commonOptions
  *   The common options to consider when writing
  * @param symbolsOutput
  *   The symbol table
  * @param refMap
  *   The reference map for looking up references
  * @param usage
  *   The usage statistics
  * @param passUtilities
  *   The PassUtilities from running the various passes
  */
case class DotdockTheme(
  filePath: Path,
  commonOptions: CommonOptions,
  symbolsOutput: SymbolsOutput,
  refMap: ReferenceMap,
  usage: Usages,
  passUtilities: PassUtilities
) extends MarkdownWriter(filePath, commonOptions, symbolsOutput, refMap, usage, passUtilities) {
  final val name: String = DotdockTheme.name
}
