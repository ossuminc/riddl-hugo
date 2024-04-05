package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{Adaptor, AdaptorOption, OccursInAdaptor}

trait AdaptorWriter { this: MarkdownWriter =>

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    containerHead(adaptor, "Adaptor")
    emitDefDoc(adaptor, parents)
    p(s"Direction: ${adaptor.direction.format} ${adaptor.context.format}")
    emitProcessorToc[AdaptorOption, OccursInAdaptor](adaptor)
    emitUsage(adaptor)
    emitTerms(adaptor.terms)
    emitIndex("Adaptor", adaptor, parents)
  }


}
