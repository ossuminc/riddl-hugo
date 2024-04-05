package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait ApplicationWriter { this: MarkdownWriter =>


  def emitApplication(
   application: Application,
   stack: Seq[Definition]
 ): Unit = {
    containerHead(application, "Application")
    val parents = makeStringParents(stack)
    emitDefDoc(application, parents)
    for group <- application.groups do {
      h2(group.identify)
      list(group.elements.map(_.format))
    }
    emitUsage(application)
    emitTerms(application.terms)
    emitIndex("Application", application, parents)
  }

}
