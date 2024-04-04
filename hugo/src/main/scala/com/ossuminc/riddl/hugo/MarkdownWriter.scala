/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput, UseCaseDiagramData, mermaid}
import com.ossuminc.riddl.diagrams.mermaid.{ContextMapDiagram, DomainMapDiagram, EntityRelationshipDiagram, UseCaseDiagram, UseCaseDiagramSupport}
import com.ossuminc.riddl.hugo.themes.ThemeWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.Keywords
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.stats.{KindStats, StatsOutput, StatsPass}
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, Usages}
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.TextFileWriter

import java.nio.file.Path
import scala.annotation.unused

/** Base  */
trait MarkdownWriter extends MarkdownBasics with PassUtilities {
  
  private def mkTocSeq(
    list: Seq[Definition]
  ): Seq[String] = {
    val result = list.map(c => c.id.value)
    result
  }


  private case class Level(name: String, href: String, children: Seq[Level]) {
    override def toString: String = {
      s"{name:\"$name\",href:\"$href\",children:[${children.map(_.toString).mkString(",")}]}"
    }
  }

  private def makeData(container: Definition, parents: Seq[String]): Level = {
    Level(
      container.identify,
      makeDocLink(container, parents),
      children = {
        val newParents = container.id.value +: parents
        container.definitions
          .filter(d => d.nonEmpty && !d.isInstanceOf[OnMessageClause])
          .map(makeData(_, newParents))
      }
    )
  }

  private def emitUsage(definition: Definition): this.type = {
    usage.getUsers(definition) match {
      case users: Seq[Definition] if users.nonEmpty =>
        listOf("Used By", users)
      case _ => h2("Used By None")
    }
    usage.getUses(definition) match {
      case usages: Seq[NamedValue] if usages.nonEmpty => listOf("Uses", usages)
      case _                                          => h2("Uses Nothing")
    }
    this
  }

  def emitIndex(
    kind: String,
    top: Definition,
    parents: Seq[String]
  ): this.type = {
    if options.withGraphicalTOC then {
      h2(s"Graphical $kind Index")
      val json = makeData(top, parents).toString
      val resourceName = "js/tree-map-hierarchy2.js"
      val javascript =
        s"""
           |<div id="graphical-index">
           |  <script src="https://d3js.org/d3.v7.min.js"></script>
           |  <script src="/$resourceName"></script>
           |  <script>
           |    console.log('d3', d3.version)
           |    let data = $json ;
           |    let svg = treeMapHierarchy(data, 932);
           |    var element = document.getElementById("graphical-index");
           |    element.appendChild(svg);
           |  </script>
           |</div>
          """.stripMargin
      p(javascript)
    }
    h2(s"Textual $kind Index")
    p("{{< toc-tree >}}")
  }

  private def emitC4ContainerDiagram(
    definition: Context,
    parents: Seq[Definition]
  ): Unit = {
    val name = definition.identify
    val brief: Definition => String = { (defn: Definition) =>
      defn.brief.fold(s"$name is not described.")(_.s)
    }

    val heading = s"""C4Context
                     |  title C4 Containment Diagram for [$name]
                     |""".stripMargin.split('\n').toSeq

    val containers = parents.filter(_.isContainer).reverse
    val systemBoundaries = containers.zipWithIndex
    val openedBoundaries = systemBoundaries.map { case (dom, n) =>
      val nm = dom.id.format
      val keyword = if n == 0 then "Enterprise_Boundary" else "System_Boundary"
      " ".repeat((n + 1) * 2) + s"$keyword($nm,$nm,\"${brief(dom)}\") {"
    }
    val closedBoundaries = systemBoundaries.reverse.map { case (_, n) =>
      " ".repeat((n + 1) * 2) + "}"
    }
    val prefix = " ".repeat(parents.size * 2)
    val context_head = prefix +
      s"Boundary($name, $name, \"${brief(definition)}\") {"
    val context_foot = prefix + "}"

    val body = definition.entities.map(e => prefix + s"  System(${e.id.format}, ${e.id.format}, \"${brief(e)}\")")
    val lines: Seq[String] = heading ++ openedBoundaries ++ Seq(context_head) ++
      body ++ Seq(context_foot) ++ closedBoundaries
    emitMermaidDiagram(lines)
  }

  private def emitTerms(terms: Seq[Term]): Unit = {
    list(
      "Terms",
      terms.map(t => (t.id.format, t.brief.map(_.s).getOrElse("{no brief}"), t.description))
    )
  }

  private def emitFields(fields: Seq[Field]): Unit = {
    list(fields.map { field =>
      (field.id.format, field.typeEx.format, field.brief, field.description)
    })
  }

  private def emitBriefly(
    d: Definition,
    parents: Seq[String],
    @unused level: Int = 2
  ): Unit = {
    emitTableHead(Seq("Item" -> 'C', "Value" -> 'L'))
    val brief: String =
      d.brief.map(_.s).getOrElse("Brief description missing.").trim
    emitTableRow(italic("Briefly"), brief)
    if d.isVital then {
      val authors = d.asInstanceOf[VitalDefinition[?, ?]].authorRefs
      emitTableRow(italic("Authors"), authors.map(_.format).mkString(", "))
    }
    val path = (parents :+ d.id.format).mkString(".")
    emitTableRow(italic("Definition Path"), path)
    val link = makeSourceLink(d)
    emitTableRow(italic("View Source Link"), s"[${d.loc}]($link)")
  }

  // This substitutions domain contains context referenced

  private val keywords: String = Keywords.definition_keywords.mkString("(", "|", ")")
  private val pathIdRegex = s" ($keywords) (\\w+(\\.\\w+)*)".r
  private def substituteIn(lineToReplace: String): String = {
    val matches = pathIdRegex.findAllMatchIn(lineToReplace).toSeq.reverse
    matches.foldLeft(lineToReplace) { case (line, rMatch) =>
      val kind = rMatch.group(1)
      val pathId = rMatch.group(3)

      def doSub(line: String, definition: NamedValue, isAmbiguous: Boolean = false): String = {
        val docLink = makeDocLink(definition)
        val substitution =
          if isAmbiguous then s"($kind $pathId (ambiguous))[$docLink]"
          else s" ($kind $pathId)[$docLink]"
        line.substring(0, rMatch.start) + substitution + line.substring(rMatch.end)
      }

      refMap.definitionOf[Definition](pathId) match {
        case Some(definition) => doSub(line, definition)
        case None =>
          val names = pathId.split('.').toSeq
          symbolsOutput.lookupSymbol[Definition](names) match
            case Nil                => line
            case ::((head, _), Nil) => doSub(line, definition = head)
            case ::((head, _), _)   => doSub(line, definition = head, isAmbiguous = true)
      }
    }
  }

  def emitDescription(d: Option[Description], level: Int = 2): this.type = {
    d match {
      case None => this
      case Some(desc) =>
        heading("Description", level)
        val substitutedDescription: Seq[String] = for {
          line <- desc.lines.map(_.s)
          newLine = substituteIn(line)
        } yield {
          newLine
        }
        substitutedDescription.foreach(p)
        this
    }
  }

  private def emitOptions[OT <: OptionValue](
    options: Seq[OT],
    level: Int = 2
  ): this.type = {
    list("RiddlOptions", options.map(_.format), level)
    this
  }

  private def emitDefDoc(
    definition: Definition,
    parents: Seq[String],
    level: Int = 2
  ): this.type = {
    emitBriefly(definition, parents, level)
    emitDescription(definition.description, level)
  }

  private def emitShortDefDoc(
    definition: Definition
  ): this.type = {
    definition.brief.foreach(b => p(italic(b.s)))
    definition.description.foreach(d => p(d.lines.mkString("\n")))
    this
  }

  private def makePathIdRef(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    parents.headOption match
      case None => ""
      case Some(parent) =>
        val resolved = refMap.definitionOf[Definition](pid, parent)
        resolved match
          case None => s"unresolved path: ${pid.format}"
          case Some(res) =>
            val slink = makeSourceLink(res)
            resolved match
              case None => s"unresolved path: ${pid.format}"
              case Some(definition) =>
                val pars = makeStringParents(parents.drop(1))
                val link = makeDocLink(definition, pars)
                s"[${resolved.head.identify}]($link) [{{< icon \"gdoc_code\" >}}]($slink)"
  }

  private def makeTypeName(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    parents.headOption match
      case None => s"unresolved path: ${pid.format}"
      case Some(parent) =>
        refMap.definitionOf[Definition](pid, parent) match {
          case None                   => s"unresolved path: ${pid.format}"
          case Some(defn: Definition) => defn.id.format
        }
  }

  private def makeTypeName(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, _, pid)      => makeTypeName(pid, parents)
      case EntityReferenceTypeExpression(_, pid) => makeTypeName(pid, parents)
      case UniqueId(_, pid)                      => makeTypeName(pid, parents)
      case Alternation(_, of) =>
        of.map(ate => makeTypeName(ate.pathId, parents))
          .mkString("-")
      case _: Mapping                        => "Mapping"
      case _: Aggregation                    => "Aggregation"
      case _: AggregateUseCaseTypeExpression => "Message"
      case _                                 => typeEx.format
    }
    name.replace(" ", "-")
  }

  private def resolveTypeExpression(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    typeEx match {
      case a: AliasedTypeExpression =>
        s"Alias of ${makePathIdRef(a.pathId, parents)}"
      case er: EntityReferenceTypeExpression =>
        s"Entity reference to ${makePathIdRef(er.entity, parents)}"
      case uid: UniqueId =>
        s"Unique identifier for entity ${makePathIdRef(uid.entityPath, parents)}"
      case alt: Alternation =>
        val data = alt.of.map { (te: AliasedTypeExpression) =>
          makePathIdRef(te.pathId, parents)
        }
        s"Alternation of: " + data.mkString(", ")
      case agg: Aggregation =>
        val data = agg.fields.map { (f: Field) =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        "Aggregation of:" + data.mkString(", ")
      case mt: AggregateUseCaseTypeExpression =>
        val data = mt.fields.map { (f: Field) =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        s"${mt.usecase.useCase} message of: " + data.mkString(", ")
      case _ => typeEx.format
    }
  }

  private def emitAggregateMembers(agg: AggregateTypeExpression, parents: Seq[Definition]): this.type = {
    val data = agg.contents.map {
      case f: AggregateValue => (f.id.format, resolveTypeExpression(f.typeEx, parents))
      case _                 => ("", "")
    }
    list(data.filterNot(t => t._1.isEmpty && t._2.isEmpty))
    this
  }

  private def emitTypeExpression(
    typeEx: TypeExpression,
    parents: Seq[Definition],
    headLevel: Int = 2
  ): Unit = {
    typeEx match {
      case a: AliasedTypeExpression =>
        heading("Alias Of", headLevel)
        p(makePathIdRef(a.pathId, parents))
      case er: EntityReferenceTypeExpression =>
        heading("Entity Reference To", headLevel)
        p(makePathIdRef(er.entity, parents))
      case uid: UniqueId =>
        heading("Unique Identifier To", headLevel)
        p(s"Entity ${makePathIdRef(uid.entityPath, parents)}")
      case alt: Alternation =>
        heading("Alternation Of", headLevel)
        val data = alt.of.map { (te: AliasedTypeExpression) =>
          makePathIdRef(te.pathId, parents)
        }
        list(data)
      case agg: Aggregation =>
        heading("Aggregation Of", headLevel)
        emitAggregateMembers(agg, parents)
      case mt: AggregateUseCaseTypeExpression =>
        heading(s"${mt.usecase.format} Of", headLevel)
        emitAggregateMembers(mt, parents)
      case map: Mapping =>
        heading("Mapping Of", headLevel)
        val from = resolveTypeExpression(map.from, parents)
        val to = resolveTypeExpression(map.to, parents)
        p(s"From:\n: $from").nl
        p(s"To:\n: $to")
      case en: Enumeration =>
        heading("Enumeration Of", headLevel)
        val data = en.enumerators.map { (e: Enumerator) =>
          val docBlock = e.brief.map(_.s).toSeq ++
            e.description.map(_.lines.map(_.s)).toSeq.flatten
          (e.id.format, docBlock)
        }
        list(data)
      case Pattern(_, strs) =>
        heading("Pattern Of", headLevel)
        list(strs.map("`" + _.s + "`"))
      case _ =>
        heading("Type", headLevel)
        p(resolveTypeExpression(typeEx, parents))
    }
  }

  def emitType(typ: Type, stack: Seq[Definition]): Unit = {
    val suffix = typ.typ match {
      case mt: AggregateUseCaseTypeExpression => mt.usecase.useCase
      case _                                  => "Type"
    }
    containerHead(typ, suffix)
    emitDefDoc(typ, makeStringParents(stack))
    emitTypeExpression(typ.typ, typ +: stack)
    emitUsage(typ)
  }

  private def emitTypesToc(definition: WithTypes): Unit = {
    val groups = definition.types
      .groupBy { typ =>
        typ.typ match {
          case mt: AggregateUseCaseTypeExpression => mt.usecase.format
          case _                                  => "Others"
        }
      }
      .toSeq
      .sortBy(_._1)
    h2("Types")
    for (label, list) <- groups do { toc(label, mkTocSeq(list), 3) }
  }

  private def emitVitalDefinitionTail[OV <: OptionValue, DEF <: RiddlValue](vd: VitalDefinition[OV, DEF]): Unit = {
    emitOptions(vd.options)
    emitTerms(vd.terms)
    emitUsage(vd)
    if vd.authorRefs.nonEmpty then toc("Authors", vd.authorRefs.map(_.format))
  }

  private def emitProcessorToc[OV <: OptionValue, DEF <: RiddlValue](processor: Processor[OV, DEF]): Unit = {
    if processor.types.nonEmpty then emitTypesToc(processor)
    if processor.constants.nonEmpty then toc("Constants", mkTocSeq(processor.constants))
    if processor.functions.nonEmpty then toc("Functions", mkTocSeq(processor.functions))
    if processor.invariants.nonEmpty then toc("Invariants", mkTocSeq(processor.invariants))
    if processor.handlers.nonEmpty then toc("Handlers", mkTocSeq(processor.handlers))
    if processor.inlets.nonEmpty then toc("Inlets", mkTocSeq(processor.inlets))
    if processor.outlets.nonEmpty then toc("Outlets", mkTocSeq(processor.outlets))
    emitVitalDefinitionTail[OV, DEF](processor)
  }

  private def emitAuthorInfo(authors: Seq[Author], level: Int = 2): this.type = {
    for a <- authors do {
      val items = Seq("Name" -> a.name.s, "Email" -> a.email.s) ++
        a.organization.fold(Seq.empty[(String, String)])(ls => Seq("Organization" -> ls.s)) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items, level)
    }
    this
  }

  def emitDomain(
    domain: Domain,
    parents: Seq[String],
    summary: Option[String],
    diagram: DomainMapDiagram
  ): this.type = {
    containerHead(domain, "Domain")
    emitDefDoc(domain, parents)
    heading("Domain Map")
    emitMermaidDiagram(diagram.generate)
    toc("Subdomains", mkTocSeq(domain.domains))
    toc("Contexts", mkTocSeq(domain.contexts))
    toc("Applications", mkTocSeq(domain.applications))
    toc("Epics", mkTocSeq(domain.epics))
    emitTypesToc(domain)
    summary match {
      case Some(link) =>
        h3(s"[Message Summary]($link)")
      case None =>
    }
    emitUsage(domain)
    emitTerms(domain.terms)
    emitAuthorInfo(domain.authors)
    emitIndex("Domain", domain, parents)
    this
  }

  private def emitInputOutput(
    input: Option[Aggregation],
    output: Option[Aggregation]
  ): this.type = {
    if input.nonEmpty then
      h4("Requires (Input)")
      emitFields(input.get.fields)
    if output.nonEmpty then
      h4("Yields (Output)")
      output match
        case None      =>
        case Some(agg) => emitFields(agg.fields)
    this
  }

  def emitFunction(function: Function, parents: Seq[String]): this.type = {
    containerHead(function, "Function")
    h2(function.id.format)
    emitDefDoc(function, parents)
    emitTypesToc(function)
    emitInputOutput(function.input, function.output)
    codeBlock("Statements", function.statements)
    emitUsage(function)
    emitTerms(function.terms)
    this
  }

  private def emitContextMap(context: Context, diagram: Option[ContextMapDiagram]): this.type = {
    if diagram.nonEmpty then
      h2("Context Map")
      val lines = diagram.get.generate
      emitMermaidDiagram(lines)
    end if
    this
  }

  def emitContext(context: Context, stack: Seq[Definition], diagram: Option[ContextMapDiagram]): this.type = {
    containerHead(context, "Context")
    val parents = makeStringParents(stack)
    emitDefDoc(context, parents)
    emitContextMap(context, diagram)
    emitOptions(context.options)
    emitTypesToc(context)
    toc("Entities", mkTocSeq(context.entities))
    toc("Adaptors", mkTocSeq(context.adaptors))
    toc("Sagas", mkTocSeq(context.sagas))
    toc("Streamlets", mkTocSeq(context.streamlets))
    list("Connections", mkTocSeq(context.connectors))
    emitProcessorToc[ContextOption, OccursInContext](context)
    // TODO: generate a diagram for the processors and pipes
    emitIndex("Context", context, parents)
    this
  }

  def emitState(
    state: State,
    fields: Seq[Field],
    parents: Seq[Definition]
  ): this.type = {
    containerHead(state, "State")
    emitDefDoc(state, makeStringParents(parents))
    emitERD(state.id.format, fields, parents)
    h2("Fields")
    emitFields(fields)
    emitUsage(state)
  }

  private def emitERD(
    name: String,
    fields: Seq[Field],
    parents: Seq[Definition]
  ): this.type = {
    h2("Entity Relationships")
    val erd = EntityRelationshipDiagram(refMap)
    val lines = erd.generate(name, fields, parents)
    emitMermaidDiagram(lines)
    this
  }

  private def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if invariants.nonEmpty then {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        list(invariant.condition.map(_.format).toSeq)
        emitDescription(invariant.description, level = 4)
      }
    }
    this
  }

  def emitHandler(handler: Handler, parents: Seq[String]): this.type = {
    containerHead(handler, "Handler")
    emitDefDoc(handler, parents)
    handler.clauses.foreach { clause =>
      clause match {
        case oic: OnInitClause        => h3(oic.kind)
        case omc: OnMessageClause     => h3(clause.kind + " " + omc.msg.format)
        case otc: OnTerminationClause => h3(otc.kind)
        case ooc: OnOtherClause       => h3(ooc.kind)
      }
      emitShortDefDoc(clause)
      codeBlock("Statements", clause.statements, 4)
    }
    emitUsage(handler)
    this
  }

  private def emitFiniteStateMachine(
    @unused entity: Entity
  ): this.type = { this }

  def emitEntity(entity: Entity, parents: Seq[String]): this.type = {
    containerHead(entity, "Entity")
    emitDefDoc(entity, parents)
    emitOptions(entity.options)
    if entity.hasOption[EntityIsFiniteStateMachine] then {
      h2("Finite State Machine")
      emitFiniteStateMachine(entity)
    }
    emitInvariants(entity.invariants)
    emitTypesToc(entity)
    // for state <- entity.states do emitState(state,)
    toc("States", mkTocSeq(entity.states))
    toc("Functions", mkTocSeq(entity.functions))
    toc("Handlers", mkTocSeq(entity.handlers))
    emitUsage(entity)
    emitTerms(entity.terms)
    emitIndex("Entity", entity, parents)
  }

  private def emitSagaSteps(actions: Seq[SagaStep]): this.type = {
    h2("Saga Actions")
    actions.foreach { step =>
      h3(step.identify)
      emitShortDefDoc(step)
      list(typeOfThing = "Do Statements", step.doStatements.map(_.format), 4)
      list(typeOfThing = "Undo Statements", step.doStatements.map(_.format), 4)
    }
    this
  }

  def emitSaga(saga: Saga, parents: Seq[String]): Unit = {
    containerHead(saga, "Saga")
    emitDefDoc(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps)
    emitIndex("Saga", saga, parents)
    emitUsage(saga)
    emitTerms(saga.terms)
  }

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
  }

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
    toc("Use Cases", mkTocSeq(epic.cases))
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

  def emitConnector(conn: Connector, parents: Seq[String]): this.type = {
    leafHead(conn, weight = 20)
    emitDefDoc(conn, parents)
    if conn.from.nonEmpty && conn.to.nonEmpty then {
      p(s"from ${conn.from.format} to ${conn.to.format}")

    }
    emitUsage(conn)
  }

  def emitStreamlet(proc: Streamlet, parents: Seq[Definition]): this.type = {
    leafHead(proc, weight = 30)
    val parList = makeStringParents(parents)
    emitDefDoc(proc, parList)
    h2("Inlets")
    proc.inlets.foreach { inlet =>
      val typeRef = makePathIdRef(inlet.type_.pathId, parents)
      h3(inlet.id.format)
      p(typeRef)
      emitShortDefDoc(inlet)
    }
    h2("Outlets")
    proc.outlets.foreach { outlet =>
      val typeRef = makePathIdRef(outlet.type_.pathId, parents)
      h3(outlet.id.format)
      p(typeRef)
      emitShortDefDoc(outlet)
    }
    emitUsage(proc)
    emitTerms(proc.terms)
    emitIndex("Processor", proc, parList)
  }

  def emitProjector(
    projector: Projector,
    parents: Seq[String]
  ): this.type = {
    containerHead(projector, "Projector")
    emitDefDoc(projector, parents)

    emitProcessorToc[ProjectorOption, OccursInProjector](projector)
    emitIndex("Projector", projector, parents)
  }

  def emitRepository(
    repository: Repository,
    parents: Seq[String]
  ): this.type = {
    containerHead(repository, "Repository")
    emitDefDoc(repository, parents)
    emitProcessorToc[RepositoryOption, OccursInRepository](repository)
    emitIndex("Repository", repository, parents)
  }

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    containerHead(adaptor, "Adaptor")
    emitDefDoc(adaptor, parents)
    p(s"Direction: ${adaptor.direction.format} ${adaptor.context.format}")
    emitProcessorToc[AdaptorOption, OccursInAdaptor](adaptor)
    emitIndex("Adaptor", adaptor, parents)
  }


  private def makeIconLink(id: String, title: String, link: String): String = {
    if link.nonEmpty then { s"[{{< icon \"$id\" >}}]($link \"$title\")" }
    else { "" }
  }

  private def emitTermRow(entry: GlossaryEntry): Unit = {
    val source_link = makeIconLink("gdoc_github", "Source Link", entry.sourceLink)
    val term = s"[${mono(entry.term)}](${entry.link})$source_link"
    val concept_link =
      s"<small>[${entry.kind.toLowerCase}](https://riddl.tech/concepts/${entry.kind.toLowerCase}/)</small>"
    emitTableRow(term, concept_link, entry.brief)
  }

  def emitGlossary(
    weight: Int,
    terms: Seq[GlossaryEntry]
  ): this.type = {
    fileHead("Glossary Of Terms", weight, Some("A generated glossary of terms"))

    emitTableHead(Seq("Term" -> 'C', "Type" -> 'C', "Brief Description" -> 'L'))

    terms.sortBy(_.term).foreach { entry => emitTermRow(entry) }
    this
  }

  def emitToDoList(weight: Int, items: Seq[ToDoItem]): Unit = {
    fileHead(
      "To Do List",
      weight,
      Option("A list of definitions needing more work")
    )
    h2("Definitions With Missing Content")
    for { (author, info) <- items.groupBy(_.author) } do {
      h3(author)
      emitTableHead(
        Seq(
          "Item Name" -> 'C',
          "Path To Item" -> 'C'
        )
      )
      for { item <- info.map { item => item.item -> s"[${item.path}](${item.link})" } } do
        emitTableRow(item._1, item._2)
    }
  }

  def emitStatistics(weight: Int): this.type = {
    fileHead(
      "Model Statistics",
      weight,
      Some("Statistical information about the RIDDL model documented")
    )

    val stats = outputs.outputOf[StatsOutput](StatsPass.name).getOrElse(StatsOutput())
    emitTableHead(
      Seq(
        "Category" -> 'L',
        "count" -> 'R',
        "% of All" -> 'R',
        "% documented" -> 'R',
        "number empty" -> 'R',
        "avg completeness" -> 'R',
        "avg complexity" -> 'R',
        "avg containment" -> 'R'
      )
    )
    val total_stats: KindStats = stats.categories.getOrElse("All", KindStats())
    stats.categories.foreach { case (key, s) =>
      emitTableRow(
        key,
        s.count.toString,
        f"%%1.2f".format(s.percent_of_all(total_stats.count)),
        f"%%1.2f".format(s.percent_documented),
        s.numEmpty.toString,
        f"%%1.3f".format(s.completeness),
        f"%%1.3f".format(s.complexity),
        f"%%1.3f".format(s.averageContainment)
      )
    }
    this
  }

  def emitMessageSummary(domain: Domain, messages: Seq[MessageInfo]): Unit = {
    fileHead(
      s"${domain.identify} Message Summary",
      containerWeight + 5,
      Some(s"Message Summary for ${domain.identify}")
    )
    emitTableHead(
      Seq(
        "Name" -> 'C',
        "Users" -> 'C',
        "Description" -> 'L'
      )
    )

    for {
      message <- messages
    } do {
      emitTableRow(
        message.message,
        message.users,
        message.description
      )
    }
  }
}
