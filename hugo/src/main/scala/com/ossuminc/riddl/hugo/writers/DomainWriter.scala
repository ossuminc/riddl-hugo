package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.DomainMapDiagram
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait DomainWriter { this: MarkdownWriter =>

  def emitDomain(domain: Domain, parents: Parents): Unit = {
    val diagram = DomainMapDiagram(domain)

    containerHead(domain, "Domain")
    emitDefDoc(domain, parents)
    h2("Domain Map")
    emitMermaidDiagram(diagram.generate)
    emitTypes(domain, domain +: parents)
    emitUsage(domain)
    emitAuthorInfo(domain.authors)
    emitTerms(domain.terms)
    definitionToc("Subdomains", domain.domains)
    definitionToc("Contexts", domain.contexts)
    definitionToc("Applications", domain.applications)
    definitionToc("Epics", domain.epics)
  }


}
