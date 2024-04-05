package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{OccursInProjector, Projector, ProjectorOption}

trait ProjectorWriter { this: MarkdownWriter =>

  def emitProjector(
                     projector: Projector,
                     parents: Seq[String]
                   ): this.type = {
    containerHead(projector, "Projector")
    emitDefDoc(projector, parents)

    emitProcessorToc[ProjectorOption, OccursInProjector](projector)
    emitUsage(projector)
    emitTerms(projector.terms)
    emitIndex("Projector", projector, parents)
  }


}
