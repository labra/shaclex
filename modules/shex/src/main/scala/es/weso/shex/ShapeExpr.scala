package es.weso.shex

import es.weso.rdf.PrefixMap
import es.weso.rdf.nodes.IRI
import es.weso.shex.extend.Extend
import es.weso.utils.EitherUtils._
import es.weso.utils.OptionListUtils._

abstract sealed trait ShapeExpr {
  def id: Option[ShapeLabel]
  def addId(lbl: ShapeLabel): ShapeExpr

  def showPrefixMap(pm: PrefixMap) = {
    import es.weso.shex.compact.CompactShow._
    showShapeExpr(this, pm)
  }

  def paths(schema: Schema): Either[String,List[Path]]
  def addAnnotations(as: List[Annotation]): ShapeExpr
  def addSemActs(as: List[SemAct]): ShapeExpr
  def getShapeRefs(schema: Schema): List[ShapeLabel]

}

object ShapeExpr {
  def any: ShapeExpr = Shape.empty

  def fail: ShapeExpr = NodeConstraint.valueSet(List(), List())
}

case class ShapeOr(id: Option[ShapeLabel],
                   shapeExprs: List[ShapeExpr],
                   annotations: Option[List[Annotation]],
                   actions: Option[List[SemAct]]
                  ) extends ShapeExpr {
  def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  override def paths(schema: Schema): Either[String,List[Path]] = sequence(shapeExprs.map(_.paths(schema))).map(_.flatten)

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }

  override def getShapeRefs(schema: Schema) = shapeExprs.map(_.getShapeRefs(schema)).flatten
}

object ShapeOr {
  def fromShapeExprs(ses: List[ShapeExpr]): ShapeOr =
    ShapeOr(None,ses,None,None)
}

case class ShapeAnd(id: Option[ShapeLabel],
                    shapeExprs: List[ShapeExpr],
                    annotations: Option[List[Annotation]],
                    actions: Option[List[SemAct]]
                   ) extends ShapeExpr {
  def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  override def paths(schema: Schema): Either[String,List[Path]] = sequence(shapeExprs.map(_.paths(schema))).map(_.flatten)

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }
  override def getShapeRefs(schema: Schema) = shapeExprs.map(_.getShapeRefs(schema)).flatten
}

object ShapeAnd {
  def fromShapeExprs(ses: List[ShapeExpr]): ShapeAnd =
    ShapeAnd(None,ses,None,None)
}

case class ShapeNot(id: Option[ShapeLabel],
                    shapeExpr: ShapeExpr,
                    annotations: Option[List[Annotation]],
                    actions: Option[List[SemAct]]
                   ) extends ShapeExpr {
  def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  override def paths(schema: Schema): Either[String,List[Path]] =
    shapeExpr.paths(schema)

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }
  override def getShapeRefs (schema: Schema)= shapeExpr.getShapeRefs(schema)
}

object ShapeNot {
  def fromShapeExpr(se: ShapeExpr): ShapeNot =
    ShapeNot(None,se,None,None)
}

case class NodeConstraint(
                           id: Option[ShapeLabel],
                           nodeKind: Option[NodeKind],
                           datatype: Option[IRI],
                           xsFacets: List[XsFacet],
                           values: Option[List[ValueSetValue]],
                           annotations: Option[List[Annotation]],
                           actions: Option[List[SemAct]]
                         ) extends ShapeExpr {
  override def addId(lbl: ShapeLabel) = {
    this.copy(id = Some(lbl))
  }

  override def paths(schema: Schema): Either[String,List[Path]] = Right(List())

  override def addAnnotations(as: List[Annotation]): NodeConstraint = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): NodeConstraint = {
    this.copy(actions = maybeAddList(actions,as))
  }

  override def getShapeRefs(s:Schema) = List()

}

object NodeConstraint {

  def empty = NodeConstraint(
    id = None,
    nodeKind = None,
    datatype = None,
    xsFacets = List(),
    values = None,
    annotations = None,
    actions = None
  )

  def nodeKind(nk: NodeKind, facets: List[XsFacet]): NodeConstraint =
    NodeConstraint.empty.copy(
      nodeKind = Some(nk),
      xsFacets = facets)

  def nodeKind(idLabel: Option[ShapeLabel], nk: NodeKind, facets: List[XsFacet]): NodeConstraint =
    NodeConstraint.empty.copy(
      id = idLabel,
      nodeKind = Some(nk),
      xsFacets = facets)

  def datatype(dt: IRI,
               facets: List[XsFacet]): NodeConstraint =
    NodeConstraint.empty.copy(
      datatype = Some(dt),
      xsFacets = facets)

  def valueSet(vs: List[ValueSetValue],
               facets: List[XsFacet]): NodeConstraint =
    NodeConstraint.empty.copy(
      values = Some(vs),
      xsFacets = facets)

  def xsFacets(facets: List[XsFacet]): NodeConstraint =
    NodeConstraint.empty.copy(xsFacets = facets)


}

case class Shape(
                  id: Option[ShapeLabel],
                  virtual: Option[Boolean],
                  closed: Option[Boolean],
                  extra: Option[List[IRI]], // TODO: Extend extras to handle Paths?
                  expression: Option[TripleExpr],
                  _extends: Option[List[ShapeExpr]],
                  annotations: Option[List[Annotation]],
                  actions: Option[List[SemAct]]
                ) extends ShapeExpr with Extend {
  def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))

  def isVirtual: Boolean =
    virtual.getOrElse(Shape.defaultVirtual)

  def isClosed: Boolean =
    closed.getOrElse(Shape.defaultClosed)

  // Converts IRIs to direct paths
  def extraPaths =
    extra.getOrElse(List()).map(Direct(_))

  def getExtra: List[IRI] = extra.getOrElse(Shape.emptyExtra)
  def getExtend: List[ShapeExpr] = _extends.getOrElse(Shape.emptyExtends)
  def getAnnotations: List[Annotation] = annotations.getOrElse(Shape.emptyAnnotations)
  def getActions: List[SemAct] = actions.getOrElse(Shape.emptySemActs)

  def isEmpty: Boolean = {
    this.id == None &&
    this.isVirtual == Shape.defaultVirtual &&
    this.isClosed == Shape.defaultClosed &&
    getExtra == Shape.emptyExtra &&
    getExtend == Shape.emptyExtends &&
    getAnnotations == Shape.emptyAnnotations &&
    getActions == Shape.emptySemActs
  }

  // def tripleExpr = expression.getOrElse(TripleExpr.any)
  private def extend(s: ShapeExpr): Option[List[ShapeExpr]] = s match {
    case s: Shape => s._extends
    case _ => None
  }

  private def expr(s: ShapeExpr): Option[TripleExpr] = s match {
    case s: Shape => s.expression
    case _ => None
  }

  /**
  * Return the paths that are mentioned in a shape
    * @param schema Schema to which the shape belongs, it is needed to resolve references to other shapes
    * @return List of paths or error in case the shape is not well defined (may have bad references)
    */
  def paths(schema: Schema): Either[String,List[Path]] = {
    def getPath(s: ShapeExpr): Option[List[Path]] = s match {
      case s: Shape => Some(s.expression.map(_.paths(schema)).getOrElse(List()))
      case _ => Some(List())
    }
    def combinePaths(p1: List[Path],
                     p2: List[Path]
                    ): List[Path] = p1 ++ p2
    extendCheckingVisited(this, getShapeExpr(schema), extend, combinePaths, getPath).map(_.getOrElse(List()))
  }


  def extendExpression(schema: Schema): Either[String,Option[TripleExpr]] = {
    def combine(e1: TripleExpr, e2: TripleExpr): TripleExpr = {
      EachOf(None,List(e1,e2),None,None,None,None)
    }
    extendCheckingVisited(this, getShapeExpr(schema), extend, combine, expr)
  }

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }

  override def getShapeRefs(schema: Schema) = expression.map(_.getShapeRefs(schema)).getOrElse(List())

  private def getShapeExpr(schema: Schema)(e: ShapeExpr): Either[String,ShapeExpr] = e match {
    case ShapeRef(lbl,_,_) => schema.getShape(lbl)
    case _ => Right(e)
  } /* for {
    label <- e.id.toRight(s"getShapeExpr: Expression $e has no Label")
    shapeExpr <- schema.getShape(label)
  } yield shapeExpr */

}

object Shape {
  def empty: Shape = Shape(
    id = None,
    virtual = None,
    closed = None,
    extra = None,
    expression = None,
    _extends = None,
    actions = None,
    annotations = None
  )

  def defaultVirtual = false
  def defaultClosed = false
  def emptyExtra = List[IRI]()
  def emptyExtends = List[ShapeExpr]()
  def emptySemActs = List[SemAct]()
  def emptyAnnotations = List[Annotation]()
  def defaultExpr = None

  def expr(te: TripleExpr): Shape = {
    Shape.empty.copy(expression = Some(te))
  }


}

case class ShapeRef(reference: ShapeLabel,
                    annotations: Option[List[Annotation]],
                    actions: Option[List[SemAct]]
                   ) extends ShapeExpr {
  def id = None
  def addId(lbl: ShapeLabel) = this

  override def paths(schema: Schema): Either[String,List[Path]] = for {
    se <- schema.getShape(reference)
    ps <- se.paths(schema)
  } yield ps

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }
  override def getShapeRefs(s: Schema) = List(reference)
}

case class ShapeExternal(id: Option[ShapeLabel],
                         annotations: Option[List[Annotation]],
                         actions: Option[List[SemAct]]
                        ) extends ShapeExpr {
  def addId(lbl: ShapeLabel) = this.copy(id = Some(lbl))
  override def paths(schema: Schema): Either[String,List[Path]] = Right(List())

  override def addAnnotations(as: List[Annotation]): ShapeExpr = {
    this.copy(annotations = maybeAddList(annotations, as))
  }
  override def addSemActs(as: List[SemAct]): ShapeExpr = {
    this.copy(actions = maybeAddList(actions,as))
  }
  override def getShapeRefs (schema: Schema) =  List()
}


object ShapeExternal {
  def empty: ShapeExternal = ShapeExternal(None,None,None)
}
