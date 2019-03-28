package es.weso.shex.implicits
import cats._
import cats.implicits._
import es.weso.rdf.nodes._
import es.weso.shex._
import es.weso.rdf._
import es.weso.rdf.PREFIXES._
import compact.CompactShow
import es.weso.rdf.operations.Comparisons._


object showShEx {

  implicit lazy val showSchema: Show[Schema] = new Show[Schema] {
    final def show(s: Schema): String =
      CompactShow.showSchema(s) /*{
      s"Schema(${optShow(s.prefixes)}, ${optShow(s.base)}, ${optShow(s.startActs)}, ${optShow(s.start)}, ${optShow(s.shapes)})"
    } */
  }

  implicit lazy val showPrefixMap: Show[PrefixMap] = new Show[PrefixMap] {
    final def show(pm: PrefixMap): String = {
      pm.toString
    }
  }

  implicit lazy val showMax = new Show[Max] {
    final def show(a: Max): String = a match {
      case Star => "*"
      case IntMax(n) => n.show
    }
  }

  implicit lazy val showShapeExpr: Show[ShapeExpr] = new Show[ShapeExpr] {
    final def show(a: ShapeExpr): String = a match {
      case ShapeOr(id, shapes,_,_) => s"${optShow(id)} ${shapes.map(_.show).mkString(" OR ")})"
      case ShapeAnd(id, shapes,_,_) => s"${optShow(id)}, ${shapes.map(_.show).mkString(" AND ")})"
      case ShapeNot(id, shape,_,_) => s"${optShow(id)} NOT ${shape.show})"
      case s: Shape => s.show
      case nc: NodeConstraint => nc.show
      case ShapeRef(r,_,_) => s"@${r.show}"
      case ShapeExternal(id,_,_) => s"${optShow(id)} EXternal"
    }
  }

  implicit lazy val showShape: Show[Shape] = new Show[Shape] {
    final def show(a: Shape): String = a match {
      case Shape(None,None,None,None,None,None,None,None) => "."
      case _ =>
        s"${optShow(a.id)}${optShowBoolean(a.virtual, "VIRTUAL")}${optShowBoolean(a.closed,"CLOSED")}${optShowExtras(a.extra)}${optShowExtends(a._extends)} { ${optShow(a.expression)} ${optShowLs(a.actions,"\n")} }"
    }
  }

  implicit lazy val showNodeConstraint: Show[NodeConstraint] = new Show[NodeConstraint] {
    final def show(a: NodeConstraint): String =
      s"${optShow(a.id)} ${optShow(a.nodeKind)} ${optShow(a.datatype)} ${showLs(a.xsFacets," ")} ${optShowValues(a.values)})"
  }

  implicit lazy val showNodeKind: Show[NodeKind] = new Show[NodeKind] {
    final def show(a: NodeKind): String = a match {
      case IRIKind => "iri"
      case BNodeKind => "bnode"
      case NonLiteralKind => "nonLiteral"
      case LiteralKind => "literal"
    }
  }

  implicit lazy val showValueSetValue: Show[ValueSetValue] = new Show[ValueSetValue] {
    final def show(a: ValueSetValue): String = a match {
      case IRIValue(iri) => iri.show
      case StringValue(s) => "\"" + s + "\""
      case DatatypeString(s, `xsd:integer`) => s
      case DatatypeString(s, d) => "\"" + s + "\"^^" + d.show
      case LangString(s, l) => "\"" + s + "\"@" + l
      case IRIStem(s) => s"stem($s)"
      case IRIStemRange(s, exclusions) => s"${s.show}~ ${optShow(exclusions)})"
      case LanguageStem(stem) => s"@$stem~ "
      case LanguageStemRange(lang,exclusions) => s"@${lang}~ ${optShow(exclusions)}"
      case LiteralStem(stem) => s"@$stem~ "
      case LiteralStemRange(stem,exclusions) => s"@${stem}~ ${optShow(exclusions)}"
      case Language(lang) => s"@$lang"
      case _ => s"Unimplemented show of $a"
    }
  }

  implicit lazy val showIRIExclusion: Show[IRIExclusion] = new Show[IRIExclusion] {
    final def show(a: IRIExclusion): String = a match {
      case IRIRefExclusion(i) => i.show
      case IRIStemExclusion(stem) => stem.show
    }
  }

  implicit lazy val showIRIStem: Show[IRIStem] = new Show[IRIStem] {
    final def show(a: IRIStem): String = a.stem.show + "~"
  }

  implicit lazy val showLiteralExclusion: Show[LiteralExclusion] = new Show[LiteralExclusion] {
    final def show(a: LiteralExclusion): String = a match {
      case LiteralStringExclusion(str) => str.show
      case LiteralStemExclusion(stem) => stem.show
    }
  }

  implicit lazy val showLiteralStem: Show[LiteralStem] = new Show[LiteralStem] {
    final def show(a: LiteralStem): String = a.stem.show + "~"
  }

  implicit lazy val showLanguageExclusion: Show[LanguageExclusion] = new Show[LanguageExclusion] {
    final def show(a: LanguageExclusion): String = a match {
      case LanguageTagExclusion(lang) => lang.show
      case LanguageStemExclusion(stem) => stem.show
    }
  }

  implicit lazy val showLanguageStem: Show[LanguageStem] = new Show[LanguageStem] {
    final def show(a: LanguageStem): String = a.stem.show + "~"
  }

  implicit lazy val showLang: Show[Lang] = new Show[Lang] {
    final def show(a: Lang): String = a.lang.show
  }

  implicit lazy val showStemValue: Show[IRIStemRangeValue] = new Show[IRIStemRangeValue] {
    final def show(a: IRIStemRangeValue): String = a match {
      case IRIStemValueIRI(i) => i.show
      case IRIStemWildcard() => "*"
    }
  }


  // TODO: It should qualify with schema's prefixMap
  implicit lazy val showIRI: Show[IRI] = new Show[IRI] {
    final def show(iri: IRI): String =
      iri.toString
  }

  implicit lazy val showPrefix: Show[Prefix] = new Show[Prefix] {
    final def show(p: Prefix): String = p.str
  }

  implicit lazy val showSemAct: Show[SemAct] = new Show[SemAct] {
    final def show(a: SemAct): String =
      "%" + a.name.show + " {" + optShow(a.code) + "}"
  }

  implicit lazy val showXsFacet: Show[XsFacet] = new Show[XsFacet] {
    final def show(a: XsFacet): String = a match {
      case Length(v) => s"${a.fieldName}(${v.show})"
      case MinLength(v) => s"${a.fieldName}(${v.show})"
      case MaxLength(v) => s"${a.fieldName}(${v.show})"
      case Pattern(v, flags) => s"${a.fieldName}(${v.show},${flags.getOrElse("").show})"
      case MinInclusive(n) => s"${a.fieldName}(${n.show})"
      case MaxInclusive(n) => s"${a.fieldName}(${n.show})"
      case MinExclusive(n) => s"${a.fieldName}(${n.show})"
      case MaxExclusive(n) => s"${a.fieldName}(${n.show})"
      case TotalDigits(n) => s"${a.fieldName}(${n.show})"
      case FractionDigits(n) => s"${a.fieldName}(${n.show})"
    }
  }

  implicit lazy val showNumericLiteral: Show[NumericLiteral] = new Show[NumericLiteral] {
    final def show(a: NumericLiteral): String = a match {
      case NumericInt(n,repr) => repr
      case NumericDouble(n,repr) => repr
      case NumericDecimal(n,repr) => repr
    }
  }

  implicit lazy val showTripleExpr: Show[TripleExpr] = new Show[TripleExpr] {
    final def show(a: TripleExpr): String = a match {
      case e: EachOf => e.show
      case e: OneOf => e.show
      case Inclusion(i) => s"&${i.show}"
      case tc: TripleConstraint => tc.show
      case e: Expr => s"Expr($e)"
    }
  }

  implicit lazy val showEachOf: Show[EachOf] = new Show[EachOf] {
    final def show(a: EachOf): String =
      s"${optShow(a.id)} ${a.expressions.map(_.show).mkString(";\n")}${optShowCard(a.optMin,a.optMax)}${optShow(a.semActs)} ${optShow(a.annotations)}"
  }


  implicit lazy val showOneOf: Show[OneOf] = new Show[OneOf] {
    final def show(a: OneOf): String =
      s"${optShow(a.id)} ${a.expressions.map(_.show).mkString("|")}${optShowCard(a.optMin,a.optMax)}${optShow(a.semActs)}${optShow(a.annotations)})"
  }

  implicit lazy val showTripleConstraint: Show[TripleConstraint] = new Show[TripleConstraint] {
    final def show(a: TripleConstraint): String =
      s"${optShow(a.id)} ${optShow(a.optInverse)}${optShow(a.optNegated)}${a.predicate.show}${optShow(a.valueExpr)}${optShowCard(a.optMin, a.optMax)}${optShow(a.semActs)}${optShow(a.annotations)}"
  }

  implicit lazy val showAnnotation: Show[Annotation] = new Show[Annotation] {
    final def show(a: Annotation): String =
      s"// ${a.predicate.show} ${a.obj.show}"
  }

  implicit lazy val showObjectValue: Show[ObjectValue] = new Show[ObjectValue] {
    final def show(a: ObjectValue): String = a match {
      case IRIValue(i) => i.show
      case StringValue(s) => "\"" + s + "\""
      case DatatypeString(s, `xsd:integer`) => s
      case DatatypeString(s, iri) => "\"" + s + "\"^^" + iri.show
      case LangString(s, l) => "\"" + s + "\"@" + l
    }
  }

  implicit lazy val showShapeLabel: Show[ShapeLabel] = new Show[ShapeLabel] {
    final def show(a: ShapeLabel): String = a match {
      case IRILabel(iri) => iri.show
      case BNodeLabel(bnode) => "_:" + bnode.id
      case Start => "Start"
    }
  }

  implicit lazy val showObjectLiteral: Show[ObjectLiteral] = new Show[ObjectLiteral] {
    final def show(a: ObjectLiteral): String = a match {
      case StringValue(s) => "\"" + s + "\""
      case DatatypeString(s, `xsd:integer`) => s
      case DatatypeString(s,iri) => "\"" + s + "\"^^" + iri.show
      case LangString(s,lang) => "\"" + s + "\"@" + lang
    }
  }

  private def showLs[A: Show](ls: List[A], sep: String): String =
    ls.map(_.show).mkString(sep)


  private def optShowValues(maybeValues: Option[List[ValueSetValue]]): String =
    maybeValues match {
      case None => ""
      case Some(ls) => "[" + ls.map(_.show).mkString(" ") + "]"
    }

  private def optShowExtends(maybeValues: Option[List[ShapeExpr]]): String =
    maybeValues match {
      case None => ""
      case Some(ls) => " extends " + ls.map(_.show).mkString(",")
    }

  private def optShowCard(maybeInt: Option[Int], maybeMax: Option[Max]): String =
    (maybeInt,maybeMax) match {
      case (None,None) => ""
      case (Some(0),Some(IntMax(1))) => s"?"
      case (Some(1),Some(IntMax(1))) => s""
      case (Some(1),Some(Star)) => s"*+"
      case (Some(0),Some(Star)) => s"**"
      case (Some(m),None) => s"{$m,}"
      case (Some(m),Some(IntMax(n))) => s"{$m,$n}"
      case (Some(m),Some(Star)) => s"{$m,*}"
      case (None,Some(Star)) => s"{0,*}"
      case (None,Some(IntMax(n))) => s"{0,$n}"
    }

  def optShow[A: Show](m: Option[A]): String =
    m match {
      case None => ""
      case Some(v) => v.show
    }

  def optShowLs[A:Show](maybeLs: Option[List[A]], sep: String): String =
    maybeLs match {
      case None => ""
      case Some(ls) => ls.map(_.show).mkString(sep)
    }

  def optShowExtras(maybeIRIs: Option[List[IRI]]): String = maybeIRIs match {
    case None => ""
    case Some(ls) => "EXTRA " + ls.map(_.show).mkString(" ")
  }

  def optShowBoolean(maybeBool: Option[Boolean], ifTrue: String): String = maybeBool match {
    case None => ""
    case Some(false) => ""
    case Some(true) => ifTrue
  }
}
