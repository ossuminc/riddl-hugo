package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.{OccursInRepository, Repository, RepositoryOption}

trait RepositoryWriter { this: MarkdownWriter =>

  def emitRepository(
    repository: Repository,
    parents: Seq[String]
  ): Unit = {
    containerHead(repository, "Repository")
    emitDefDoc(repository, parents)
    emitProcessorToc[RepositoryOption, OccursInRepository](repository)
    emitUsage(repository)
    emitTerms(repository.terms)
    emitIndex("Repository", repository, parents)
  }

}
