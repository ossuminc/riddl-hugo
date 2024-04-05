package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput, UseCaseDiagramData}
import com.ossuminc.riddl.diagrams.mermaid.{UseCaseDiagram, UseCaseDiagramSupport}
import com.ossuminc.riddl.language.AST.{Definition, Epic, UseCase, User, UserStory}

trait EpicWriter { this: MarkdownWriter =>


  def emitEpic(epic: Epic, stack: Seq[Definition]): this.type = {
    containerHead(epic, "Epic")
    val parents = makeStringParents(stack)
    emitBriefly(epic, parents)
    if epic.userStory.nonEmpty then {
      val userPid = epic.userStory.getOrElse(UserStory()).user.pathId
      val parent = stack.head
      val maybeUser = refMap.definitionOf[User](userPid, parent)
      h2("User Story")
      maybeUser match {
        case None => p(s"Unresolvable User id: ${userPid.format}")
        case Some(user) =>
          val name = user.id.value
          val role = user.is_a.s
          val us = epic.userStory.get
          val benefit = us.benefit.s
          val capability = us.capability.s
          val storyText =
            s"I, $name, as $role, want $capability, so that $benefit"
          p(italic(storyText))
      }
    }
    emitDescription(epic.description)
    list("Visualizations", epic.shownBy.map(u => s"($u)[$u]"))
    definitionToc("Use Cases", epic.cases)
    emitUsage(epic)
    emitTerms(epic.terms)
    emitIndex("Epic", epic, parents)
  }

  def emitUser(u: User, parents: Seq[String]): this.type = {
    leafHead(u, weight = 20)
    p(s"${u.identify} is a ${u.is_a.s}.")
    emitDefDoc(u, parents)
  }

  def emitUseCase(uc: UseCase, parents: Seq[Definition], sds: UseCaseDiagramSupport): Unit = {
    leafHead(uc, weight = 20)
    val parList = makeStringParents(parents)
    emitDefDoc(uc, parList)
    h2("Sequence Diagram")
    parents.headOption match
      case Some(p1) =>
        val epic = p1.asInstanceOf[Epic]
        outputs.outputOf[DiagramsPassOutput](DiagramsPass.name) match
          case Some(dpo) =>
            dpo.userCaseDiagrams.get(uc) match
              case Some(useCaseDiagramData: UseCaseDiagramData) =>
                val ucd = UseCaseDiagram(sds, useCaseDiagramData)
                val lines = ucd.generate
                emitMermaidDiagram(lines)

              case None =>
                notAvailable("Sequence diagram is not available")
            end match
          case None =>
            notAvailable("Sequence diagram is not available")
        end match
      case None =>
        notAvailable("Sequence diagram is not available")
    end match
  }
}
