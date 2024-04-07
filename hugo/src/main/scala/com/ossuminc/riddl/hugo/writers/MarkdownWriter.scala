/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid.*
import com.ossuminc.riddl.diagrams.{DiagramsPass, DiagramsPassOutput, UseCaseDiagramData, mermaid}
import com.ossuminc.riddl.hugo.writers.{AdaptorWriter, DomainWriter}
import com.ossuminc.riddl.hugo.PassUtilities
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.Keywords
import com.ossuminc.riddl.passes.resolve.{ReferenceMap, Usages}
import com.ossuminc.riddl.passes.symbols.Symbols.Parents
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.stats.{KindStats, StatsOutput, StatsPass}
import com.ossuminc.riddl.utils.{TextFileWriter, Timer}

import java.nio.file.Path
import scala.annotation.unused
import scala.collection.immutable.Seq

/** Base */
trait MarkdownWriter
    extends MarkdownBasics
    with AdaptorWriter
    with ApplicationWriter
    with ContextWriter
    with DomainWriter
    with EntityWriter
    with EpicWriter
    with ProjectorWriter
    with RepositoryWriter
    with SagaWriter
    with StreamletWriter
    with SummariesWriter
    with PassUtilities {

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

  protected def emitUsage(definition: Definition): this.type = {
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

  def emitTerms(terms: Seq[Term]): Unit = {
    list(
      "Terms",
      terms.map(t => (t.id.format, t.brief.map(_.s).getOrElse("{no brief}"), t.description))
    )
  }

  protected def emitFields(fields: Seq[Field]): Unit = {
    list(fields.map { field =>
      (field.id.format, field.typeEx.format, field.brief, field.description)
    })
  }

  protected def emitBriefly(
    d: Definition,
    parents: Parents,
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
    val path = (parents.map(_.id.value) :+ d.id.value).mkString(".")
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

  protected def emitOptions[OT <: OptionValue](
    options: Seq[OT],
    level: Int = 2
  ): this.type = {
    list("RiddlOptions", options.map(_.format), level)
    this
  }

  protected def emitDefDoc(
    definition: Definition,
    parents: Parents,
    level: Int = 2
  ): this.type = {
    emitBriefly(definition, parents, level)
    emitDescription(definition.description, level)
  }

  protected def emitShortDefDoc(
    definition: Definition
  ): this.type = {
    definition.brief.foreach(b => p(italic(b.s)))
    definition.description.foreach(d => p(d.lines.mkString("\n")))
    this
  }

  protected def makePathIdRef(
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

  protected def makeTypeName(
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

  private def emitType(typ: Type, parents: Parents): Unit = {
    h4(typ.identify)
    emitDefDoc(typ, parents)
    emitTypeExpression(typ.typ, typ +: parents)
    emitUsage(typ)
  }

  protected def emitTypes(definition: Definition & WithTypes, parents: Parents): Unit = {
    val groups = definition.types
      .groupBy { typ =>
        typ.typ match {
          case mt: AggregateUseCaseTypeExpression          => mt.usecase.format
          case AliasedTypeExpression(loc, keyword, pathId) => "Alias "
          case EntityReferenceTypeExpression(loc, entity)  => "Reference "
          case numericType: NumericType                    => "Numeric "
          case PredefinedType(str)                         => "Predefined "
          case _                                           => "Structural"
        }
      }
      .toSeq
      .sortBy(_._2.size)
    h2("Types")
    for {
      (label, list) <- groups
    } do {
      h3(label + " Types")
      for typ <- list do emitType(typ, parents)
    }
  }

  private def emitConstants(withConstants: Definition & WithConstants, parents: Parents): Unit = {
    h2("Constants")
    for { c <- withConstants.constants } do
      emitDefDoc(c, withConstants +: parents)
      p(s"* type:  ${c.typeEx.format}")
      p(s"* value: ${c.value.format}")
  }

  protected def emitAuthorInfo(authors: Seq[Author], level: Int = 2): this.type = {
    for a <- authors do {
      val items = Seq("Name" -> a.name.s, "Email" -> a.email.s) ++
        a.organization.fold(Seq.empty[(String, String)])(ls => Seq("Organization" -> ls.s)) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items, level)
    }
    this
  }

  protected def emitInputOutput(
    input: Option[Aggregation],
    output: Option[Aggregation]
  ): this.type = {
    if input.nonEmpty then
      h4("Requires (Input)")
      emitFields(input.get.fields)
    if output.nonEmpty then
      h4("Returns (Output)")
      output match
        case None      =>
        case Some(agg) => emitFields(agg.fields)
    this
  }

  def emitFunction(function: Function, parents: Parents): Unit = {
    h2(function.identify)
    emitDefDoc(function, parents, 3)
    emitTypes(function, parents)
    emitInputOutput(function.input, function.output)
    codeBlock("Statements", function.statements)
    emitUsage(function)
    emitTerms(function.terms)
  }

  private def emitFunctions(withFunc: Definition & WithFunctions, stack: Parents): Unit = {
    h2("Functions")
    for { f <- withFunc.functions } do {
      val parents = withFunc
      emitFunction(f, withFunc +: stack)
    }
  }

  protected def emitInvariants(invariants: Seq[Invariant]): this.type = {
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

  private def emitHandlers(
    withHandlers: Definition & WithHandlers,
    parents: Parents
  ): Unit = {
    h2("Handlers")
    for { h <- withHandlers.handlers } do {
      emitHandler(h, withHandlers +: parents)
    }
  }

  private def emitInlet(inlet: Inlet, parents: Parents): Unit = {
    emitDefDoc(inlet, parents, 3)
    val typeRef = makePathIdRef(inlet.type_.pathId, parents)
    p(s"Receives type $typeRef")
  }

  protected def emitInlets(withInlets: Definition & WithInlets, parents: Parents): Unit = {
    h2("Inlets")
    for { i <- withInlets.inlets } do {
      emitInlet(i, withInlets +: parents)
    }
  }

  private def emitOutlet(outlet: Outlet, parents: Parents): Unit = {
    emitDefDoc(outlet, parents, 3)
    val typeRef = makePathIdRef(outlet.type_.pathId, parents)
    p(s"Transmits type $typeRef")
  }

  protected def emitOutlets(withOutlets: Definition & WithOutlets, parents: Parents): Unit = {
    h2("Outlets")
    for { o <- withOutlets.outlets } do {
      emitOutlet(o, withOutlets +: parents)
    }
  }

  protected def emitVitalDefinitionDetails[OV <: OptionValue, CT <: RiddlValue](
    vd: VitalDefinition[OV, CT],
    stack: Parents
  ): Unit = {
    emitDefDoc(vd, stack)
    emitOptions(vd.options)
    emitTerms(vd.terms)
    emitUsage(vd)
  }
  protected def emitProcessorDetails[OV <: OptionValue, DEF <: RiddlValue](
    processor: Processor[OV, DEF],
    stack: Parents
  ): Unit = {
    emitVitalDefinitionDetails(processor, stack)
    if processor.types.nonEmpty then emitTypes(processor, stack)
    if processor.constants.nonEmpty then emitConstants(processor, stack)
    if processor.functions.nonEmpty then emitFunctions(processor, stack)
    if processor.invariants.nonEmpty then emitInvariants(processor.invariants)
    if processor.handlers.nonEmpty then emitHandlers(processor, stack)
    if processor.inlets.nonEmpty then emitInlets(processor, stack)
    if processor.outlets.nonEmpty then emitOutlets(processor, stack)
    processorToc(processor)
  }

}
