package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.DomainMapDiagram
import com.ossuminc.riddl.language.AST.*

trait DomainWriter { this: MarkdownWriter => 

  def emitDomain(domain: Domain, parents: Seq[String]): Unit = {
    val diagram = DomainMapDiagram(domain)

    containerHead(domain, "Domain")
    emitDefDoc(domain, parents)
    emitAuthorInfo(domain.authors)
    h2("Domain Map")
    emitMermaidDiagram(diagram.generate)
    definitionToc("Subdomains", domain.domains)
    definitionToc("Contexts", domain.contexts)
    definitionToc("Applications", domain.applications)
    definitionToc("Epics", domain.epics)
    emitTypesToc(domain)
    emitUsage(domain)
    emitTerms(domain.terms)
    emitIndex("Domain", domain, parents)
  }


}
