package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.ContextMapDiagram
import com.ossuminc.riddl.language.AST.{Context, ContextOption, Definition, OccursInContext}

trait ContextWriter { this: MarkdownWriter =>

  private def emitContextMap(context: Context, diagram: Option[ContextMapDiagram]): Unit = {
    if diagram.nonEmpty then
      h2("Context Map")
      val lines = diagram.get.generate
      emitMermaidDiagram(lines)
    end if
  }

  def emitContext(context: Context, stack: Seq[Definition]): Unit = {
    containerHead(context, "Context")
    val maybeDiagram = diagrams.contextDiagrams.get(context).map(data => ContextMapDiagram(context, data))
    val parents = makeStringParents(stack)
    emitDefDoc(context, parents)
    emitContextMap(context, maybeDiagram)
    emitOptions(context.options)
    emitTypesToc(context)
    definitionToc("Entities", context.entities)
    definitionToc("Adaptors", context.adaptors)
    definitionToc("Sagas", context.sagas)
    definitionToc("Streamlets", context.streamlets)
    list("Connectors", context.connectors)
    emitProcessorToc[ContextOption, OccursInContext](context)
    // TODO: generate a diagram for the processors and pipes
    emitIndex("Context", context, parents)
  }

}
