package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*

trait StreamletWriter { this: MarkdownWriter =>

  def emitConnector(conn: Connector, parents: Seq[String]): this.type = {
    leafHead(conn, weight = 20)
    emitDefDoc(conn, parents)
    if conn.from.nonEmpty && conn.to.nonEmpty then {
      p(s"from ${conn.from.format} to ${conn.to.format}")

    }
    emitUsage(conn)
  }

  def emitStreamlet(streamlet: Streamlet, parents: Seq[Definition]): this.type = {
    leafHead(streamlet, weight = 30)
    val parList = makeStringParents(parents)
    emitDefDoc(streamlet, parList)
    h2("Inlets")
    streamlet.inlets.foreach { inlet =>
      val typeRef = makePathIdRef(inlet.type_.pathId, parents)
      h3(inlet.id.format)
      p(typeRef)
      emitShortDefDoc(inlet)
    }
    h2("Outlets")
    streamlet.outlets.foreach { outlet =>
      val typeRef = makePathIdRef(outlet.type_.pathId, parents)
      h3(outlet.id.format)
      p(typeRef)
      emitShortDefDoc(outlet)
    }
    emitUsage(streamlet)
    emitTerms(streamlet.terms)
    emitIndex("Streamlet", streamlet, parList)
  }
}
