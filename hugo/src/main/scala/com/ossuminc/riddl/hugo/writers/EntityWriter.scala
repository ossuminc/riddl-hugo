package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.EntityRelationshipDiagram
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

import scala.annotation.unused

trait EntityWriter { this: MarkdownWriter =>

  private def emitState(
    state: State,
    parents: Parents
  ): this.type = {
    h2(state.identify)
    val stringParents = makeStringParents(parents)
    emitDefDoc(state, stringParents)
    val maybeType = refMap.definitionOf[Type](state.typ.pathId, state)
    val fields = maybeType match {
      case Some(typ: AggregateTypeExpression) => typ.fields
      case Some(_) => Seq.empty[Field]
      case None => Seq.empty[Field]
    }
    emitERD(state.id.format, fields, parents)
    h2("Fields")
    emitFields(fields)
    for h <- state.handlers do emitHandler(h, state.id.value +: stringParents)
    emitUsage(state)
  }

  private def emitERD(
                       name: String,
                       fields: Seq[Field],
                       parents: Seq[Definition]
                     ): Unit = {
    h2("Entity Relationships")
    val erd = EntityRelationshipDiagram(refMap)
    val lines = erd.generate(name, fields, parents)
    emitMermaidDiagram(lines)
  }

  def emitHandler(handler: Handler, parents: Seq[String]): this.type = {
    containerHead(handler, "Handler")
    emitDefDoc(handler, parents)
    handler.clauses.foreach { clause =>
      clause match {
        case oic: OnInitClause => h3(oic.kind)
        case omc: OnMessageClause => h3(clause.kind + " " + omc.msg.format)
        case otc: OnTerminationClause => h3(otc.kind)
        case ooc: OnOtherClause => h3(ooc.kind)
      }
      emitShortDefDoc(clause)
      codeBlock("Statements", clause.statements, 4)
    }
    emitUsage(handler)
    this
  }

  private def emitFiniteStateMachine(@unused entity: Entity): Unit = ()

  def emitEntity(entity: Entity, parents: Parents): Unit = {
    containerHead(entity, "Entity")
    val stringParents = makeStringParents(parents)
    emitDefDoc(entity, stringParents)
    emitOptions(entity.options)
    if entity.hasOption[EntityIsFiniteStateMachine] then {
      h2("Finite State Machine")
      emitFiniteStateMachine(entity)
    }
    emitInvariants(entity.invariants)
    emitTypesToc(entity)
    for state <- entity.states do emitState(state, entity +: parents)
    for handler <- entity.handlers do emitHandler(handler, entity.id.value +: stringParents)
    for function <- entity.functions do emitFunction(function, entity.id.value +: stringParents)
    emitUsage(entity)
    emitTerms(entity.terms)
    emitIndex("Entity", entity, stringParents)
  }

}
