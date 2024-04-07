package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{Adaptor, AdaptorOption, OccursInAdaptor}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait AdaptorWriter { this: MarkdownWriter =>

  def emitAdaptor(adaptor: Adaptor, parents: Parents): Unit = {
    containerHead(adaptor, "Adaptor")
    emitDefDoc(adaptor, parents)
    p(s"Direction: ${adaptor.direction.format} ${adaptor.context.format}")
    emitProcessorDetails[AdaptorOption, OccursInAdaptor](adaptor, parents)
    
  }
}
