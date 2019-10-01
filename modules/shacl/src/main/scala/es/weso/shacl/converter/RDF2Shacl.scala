package es.weso.shacl.converter

import com.typesafe.scalalogging.LazyLogging
import es.weso.rdf.PREFIXES._
import es.weso.rdf.{RDFBuilder, RDFReader}
import es.weso.rdf.nodes._
import es.weso.rdf.parser.RDFParser
import es.weso.rdf.path._
import es.weso.shacl.SHACLPrefixes._
import es.weso.shacl._
import es.weso.shacl.report._
import es.weso.utils.EitherUtils._
import scala.util.{Failure, Success, Try}

object RDF2Shacl extends RDFParser with LazyLogging {

  // Keep track of parsed shapes
  // TODO: Refactor this code to use a StateT
  val parsedShapes = collection.mutable.Map[RefNode, Shape]()

  // TODO: Refactor this code to avoid imperative style
  var pendingNodes = List[RDFNode]()

  val parsedPropertyGroups = collection.mutable.Map[RefNode, PropertyGroup]()

  def tryGetShacl(rdf: RDFBuilder,
                  resolveImports: Boolean): Try[Schema] =
    getShacl(rdf, resolveImports).fold(
      str => Failure(new Exception(str)),
      Success(_))

  def getShaclFromRDFReader(rdf: RDFReader): Either[String,Schema] = {
    val pm = rdf.getPrefixMap
    for {
      shapesMap <- shapesMap(rdf)
      imports <- parseImports(rdf)
      entailments <- parseEntailments(rdf)
    } yield Schema(
      pm = pm,
      imports = imports,
      entailments = entailments,
      shapesMap = shapesMap,
      propertyGroups = parsedPropertyGroups.toMap
    )
  }

  /**
   * Parses RDF content and obtains a SHACL Schema and a PrefixMap
   */
  def getShacl(rdf: RDFBuilder,
               resolveImports: Boolean = true
              ): Either[String, Schema] = {
    for {
      extendedRdf <-
        if (resolveImports) rdf.extendImports()
        else Right(rdf)
      schema <- getShaclFromRDFReader(extendedRdf)
    } yield schema
  }

  private def parseEntailments(rdf: RDFReader): Either[String, List[IRI]] =
      for {
        ts <- rdf.triplesWithPredicate(`sh:entailment`)
        iris <- sequence(ts.map(_.obj).toList.map(_.toIRI))
      } yield iris

  private def parseImports(rdf: RDFReader): Either[String, List[IRI]] =
    for {
     ts <- rdf.triplesWithPredicate(`owl:imports`)
     os <- sequence(ts.map(_.obj).toList.map(_.toIRI))
    } yield os

  type ShapesMap = Map[RefNode, Shape]

  def shapesMap(rdf: RDFReader): Either[String, ShapesMap] = {
    parsedShapes.clear()
    parsedPropertyGroups.clear()
    for {
      nodeShapes <- rdf.subjectsWithType(`sh:NodeShape`)
      propertyShapes <- rdf.subjectsWithType(`sh:PropertyShape`)
      shapes <- rdf.subjectsWithType(`sh:Shape`)
      objectsPropertyShapes <- rdf.subjectsWithProperty(`sh:property`)
      sm <-{
        val allShapes = nodeShapes ++ propertyShapes ++ shapes ++ objectsPropertyShapes
        pendingNodes = allShapes.toList
        parseShapes(rdf)
      }
    } yield sm
   }

  def parseShapes(rdf: RDFReader): Either[String, ShapesMap] = {
    pendingNodes.size match {
      case 0 => Right(parsedShapes.toMap)
      case _ => {
        val nodes = pendingNodes
        pendingNodes = List() // Cleans list of pending nodes...
        logger.debug(s"parseShapes: Nodes: ${nodes.mkString(",")}. Pending nodes: ${pendingNodes.mkString(",")}")
        parseNodes(nodes, shape)(rdf) match {
          case Left(s) => Left(s)
          case Right(vs) => // Continue parsing in case pendingNodes was filled with some values during parsing
            parseShapes(rdf)
        }
      }
    }
  }

  type ShaclParser[A] = Set[RDFNode] => RDFParser[(A, Set[RDFNode])]

  def shape: RDFParser[RefNode] = (n, rdf) => {
    val shapeRef = RefNode(n)
    if (parsedShapes.contains(shapeRef)) {
      parseOk(shapeRef)
    } else {
      for {
        shapeRef <- firstOf(nodeShape, propertyShape)(n, rdf)
      } yield {
        //        parsedShapes(shapeRef) = newShape
        shapeRef
      }
    }
  }

  def mkId(n: RDFNode): Option[IRI] = n match {
    case iri: IRI => Some(iri)
    case _ => None
  }

  def nodeShape: RDFParser[RefNode] = (n, rdf) => for {
    types <- rdfTypes(n, rdf)
    _ <- failIf(types.contains(`sh:PropertyShape`), "Node shapes must not have rdf:type sh:PropertyShape")(n, rdf)
    targets <- targets(n, rdf)
    propertyShapes <- propertyShapes(n, rdf)
    components <- components(n, rdf)
    closed <- booleanFromPredicateOptional(`sh:closed`)(n, rdf)
    deactivated <- booleanFromPredicateOptional(`sh:deactivated`)(n,rdf)
    message <- parseMessage(n, rdf)
    name <- parseMessage(n,rdf)
    description <- parseMessage(n,rdf)
    group <- parsePropertyGroup(n,rdf)
    order <- parseOrder(n,rdf)
    severity <- parseSeverity(n,rdf)
    ignoredNodes <- {
      rdfListForPredicateOptional(`sh:ignoredProperties`)(n, rdf)
    }
    ignoredIRIs <- nodes2iris(ignoredNodes)
    classes <- objectsFromPredicate(`sh:class`)(n,rdf)
  } yield {
    val shape: Shape = NodeShape(
      id = n,
      components = components.toList,
      targets = targets,
      propertyShapes = propertyShapes,
      closed = closed.getOrElse(false),
      ignoredProperties = ignoredIRIs,
      deactivated = deactivated.getOrElse(false),
      message = message,
      severity = severity,
      name = name,
      description = description,
      group = group,
      order = order,
      sourceIRI = rdf.sourceIRI
    )
    val sref = RefNode(n)
    parsedShapes += (sref -> shape)
    sref
  }

  private def parsePropertyGroup: RDFParser[Option[RefNode]] = (n,rdf) => for {
    maybeGroup <- objectFromPredicateOptional(`sh:group`)(n,rdf)
    group <- maybeGroup match {
      case None => Right(None)
      case Some(groupNode) => {
        val ref = RefNode(groupNode)
        parsedPropertyGroups.get(ref) match {
        case Some(pg) => Right(Some(ref))
        case None => for {
         labels <- objectsFromPredicate(`rdfs:label`)(n,rdf)
         order <- parseOrder(n,rdf)
        } yield {
          val pg = PropertyGroup(order,labels)
          parsedPropertyGroups += (ref -> pg)
          Some(ref)
        }
       }
      }
    }
  } yield group

  private def parseOrder: RDFParser[Option[DecimalLiteral]] = (n,rdf) => for {
    maybeOrder <- decimalLiteralFromPredicateOptional(`sh:order`)(n,rdf)
  } yield maybeOrder

  private def parseSeverity: RDFParser[Option[Severity]] = (n,rdf) => for {
    maybeIri <- iriFromPredicateOptional(`sh:severity`)(n,rdf)
  } yield maybeIri match {
      case Some(`sh:Violation`) => Some(ViolationSeverity)
      case Some(`sh:Warning`) => Some(WarningSeverity)
      case Some(`sh:Info`) => Some(InfoSeverity)
      case Some(iri) => Some(GenericSeverity(iri))
      case None => None
  }

  private def parseMessage: RDFParser[MessageMap] = (n,rdf) => for {
    nodes <- objectsFromPredicate(`sh:message`)(n,rdf)
    map <- cnvMessages(nodes)(n,rdf)
  } yield map

  private def cnvMessages(ns: Set[RDFNode]): RDFParser[MessageMap] = (n,rdf) => {
    MessageMap.fromRDFNodes(ns.toList)
  }


  def propertyShape: RDFParser[RefNode] = (n, rdf) => for {
    types <- rdfTypes(n, rdf)
    _ <- failIf(types.contains(`sh:NodeShape`), "Property shapes must not have rdf:type sh:NodeShape")(n, rdf)
    targets <- targets(n, rdf)
    nodePath <- objectFromPredicate(`sh:path`)(n, rdf)
    path <- parsePath(nodePath, rdf)
    propertyShapes <- propertyShapes(n, rdf)
    components <- components(n, rdf)
    closed <- booleanFromPredicateOptional(`sh:closed`)(n, rdf)
    ignoredNodes <- rdfListForPredicateOptional(`sh:ignoredProperties`)(n, rdf)
    deactivated <- booleanFromPredicateOptional(`sh:deactivated`)(n, rdf)
    message <- parseMessage(n, rdf)
    severity <- parseSeverity(n,rdf)
    name <- parseMessage(n,rdf)
    description <- parseMessage(n,rdf)
    group <- parsePropertyGroup(n,rdf)
    order <- parseOrder(n,rdf)
    ignoredIRIs <- {
      nodes2iris(ignoredNodes)
    }
  } yield {
    val ps = PropertyShape(
      id = n,
      path = path,
      components = components.toList,
      targets = targets,
      propertyShapes = propertyShapes,
      closed = closed.getOrElse(false),
      ignoredProperties = ignoredIRIs,
      deactivated = deactivated.getOrElse(false),
      message = message,
      severity = severity,
      name = name,
      description = description,
      order = order,
      group = group,
      sourceIRI = rdf.sourceIRI,
      annotations = List()  // TODO: Annotations should contain the values for other predicates associated with a given node
    )

    val sref = RefNode(n)
    parsedShapes += (sref -> ps)
    sref
  }

  def targets: RDFParser[Seq[Target]] =
    combineAll(
      targetNodes,
      targetClasses,
      implicitTargetClass,
      targetSubjectsOf,
      targetObjectsOf)

  def targetNodes: RDFParser[Seq[Target]] = (n, rdf) => {
    for {
      ns <- objectsFromPredicate(`sh:targetNode`)(n, rdf)
      vs <- sequenceEither(ns.toList.map(mkTargetNode))
    } yield vs
  }

  def targetClasses: RDFParser[Seq[Target]] = (n, rdf) =>
    for {
      ns <- objectsFromPredicate(`sh:targetClass`)(n, rdf)
      vs <- sequenceEither(ns.toList.map(mkTargetClass))
    } yield vs

  def implicitTargetClass: RDFParser[Seq[Target]] = (n, rdf) =>
    for {
     ts <- rdf.triplesWithSubjectPredicate(n, `rdf:type`)
     shapeTypes = ts.map(_.obj)
     rdfs_Class = rdfs + "Class"
     r <- if (shapeTypes.contains(rdfs_Class))
      mkTargetClass(n).map(Seq(_))
    else
      Right(Seq())
   } yield r

  def targetSubjectsOf: RDFParser[Seq[Target]] = (n, rdf) => {
    for {
      ns <- objectsFromPredicate(`sh:targetSubjectsOf`)(n, rdf)
      vs <- sequenceEither(ns.toList.map(mkTargetSubjectsOf))
    } yield vs
  }

  def targetObjectsOf: RDFParser[Seq[Target]] = (n, rdf) => {
    for {
      ns <- objectsFromPredicate(`sh:targetObjectsOf`)(n, rdf)
      vs <- sequenceEither(ns.toList.map(mkTargetObjectsOf))
    } yield vs
  }

  def mkTargetNode(n: RDFNode): Either[String, TargetNode] =
    parseOk(TargetNode(n))

  def mkTargetClass(n: RDFNode): Either[String, TargetClass] =
    parseOk(TargetClass(n))

  def mkTargetSubjectsOf(n: RDFNode): Either[String, TargetSubjectsOf] = n match {
    case i: IRI => parseOk(TargetSubjectsOf(i))
    case _ => parseFail(s"targetSubjectsOf requires an IRI. Obtained $n")
  }

  def mkTargetObjectsOf(n: RDFNode): Either[String, TargetObjectsOf] = n match {
    case i: IRI => parseOk(TargetObjectsOf(i))
    case _ => parseFail(s"targetObjectsOf requires an IRI. Obtained $n")
  }

/*  def isPropertyShape(node: RDFNode, rdf: RDFReader): Boolean = {
    rdf.getTypes(node).contains(sh_PropertyShape) ||
      !rdf.triplesWithSubjectPredicate(node, sh_path).isEmpty
  } */

  /*  def nodeShapes: RDFParser[Seq[NodeShape]] = (n, rdf) => {
   val id = if (n.isIRI) Some(n.toIRI) else None
   for {
     cs <- components(n,rdf)
   } yield cs.map(c => NodeShape(id, components = List(c)))
  } */

  def propertyShapes: RDFParser[Seq[RefNode]] = (n, rdf) => {
    for {
      ps <- objectsFromPredicate(`sh:property`)(n, rdf)
      vs <- sequenceEither(ps.toList.map(p => propertyShapeRef(p, rdf)))
    } yield vs
  }

  def propertyShapeRef: RDFParser[RefNode] = (n, rdf) => {
    pendingNodes = n :: pendingNodes
    parseOk(RefNode(n))
  }

  /*
  def propertyShape: RDFParser[PropertyShape] = (n, rdf) => {
    val id = if (n.isIRI) Some(n.toIRI) else None
    for {
      nodePath <- objectFromPredicate(sh_path)(n,rdf)
      path <- parsePath(nodePath,rdf)
      components <- components(n,rdf)
    } yield {
      PropertyShape(id, path, components)
    }
  } */

  def parsePath: RDFParser[SHACLPath] = (n, rdf) => {
    n match {
      case iri: IRI => Right(PredicatePath(iri))
      case bnode: BNode => someOf(
        oneOrMorePath,
        zeroOrMorePath,
        zeroOrOnePath,
        alternativePath,
        sequencePath,
        inversePath
      )(n, rdf)
      case _ => parseFail(s"Unsupported value $n for path")
    }
  }

  def inversePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNode <- objectFromPredicate(`sh:inversePath`)(n, rdf)
    path <- parsePath(pathNode, rdf)
  } yield InversePath(path)

  def oneOrMorePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNode <- objectFromPredicate(`sh:oneOrMorePath`)(n, rdf)
    path <- parsePath(pathNode, rdf)
  } yield OneOrMorePath(path)

  def zeroOrMorePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNode <- objectFromPredicate(`sh:zeroOrMorePath`)(n, rdf)
    path <- parsePath(pathNode, rdf)
  } yield ZeroOrMorePath(path)

  def zeroOrOnePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNode <- objectFromPredicate(`sh:zeroOrOnePath`)(n, rdf)
    path <- parsePath(pathNode, rdf)
  } yield ZeroOrOnePath(path)

  def alternativePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNode <- objectFromPredicate(`sh:alternativePath`)(n, rdf)
    pathNodes <- rdfList(pathNode, rdf)
    paths <- group(parsePath, pathNodes)(n, rdf)
  } yield AlternativePath(paths)

  def sequencePath: RDFParser[SHACLPath] = (n, rdf) => for {
    pathNodes <- rdfList(n, rdf)
    paths <- group(parsePath, pathNodes)(n, rdf)
  } yield {
    SequencePath(paths)
  }

  def components: RDFParser[Seq[Component]] = (n,rdf) => for {
    cs1 <- anyOf(
      pattern, languageIn, uniqueLang,
      equals, disjoint, lessThan, lessThanOrEquals,
      or, and, not, xone, qualifiedValueShape,
      nodeComponent,
      hasValue,
      in)(n,rdf)
    cs2 <- anyOfLs(
      classComponent,
      datatype,
      nodeKind,
      minCount, maxCount,
      minExclusive, maxExclusive, minInclusive, maxInclusive,
      minLength, maxLength
    )(n,rdf)
  } yield cs1 ++ cs2

  def classComponent: RDFParser[List[ClassComponent]] = (n,rdf) => for {
    cs <- {
      parsePredicateList(`sh:class`, ClassComponent)(n,rdf)
    }
  } yield {
    cs
  }

  private def datatype: RDFParser[List[Datatype]] = parsePredicateIRIList(`sh:datatype`, Datatype)

  private def minInclusive : RDFParser[List[MinInclusive]] = parsePredicateLiteralList(`sh:minInclusive`, MinInclusive)

  private def maxInclusive : RDFParser[List[MaxInclusive]] = parsePredicateLiteralList(`sh:maxInclusive`, MaxInclusive)

  private def minExclusive : RDFParser[List[MinExclusive]] = parsePredicateLiteralList(`sh:minExclusive`, MinExclusive)

  private def maxExclusive:  RDFParser[List[MaxExclusive]] = parsePredicateLiteralList(`sh:maxExclusive`, MaxExclusive)

  private def minLength: RDFParser[List[MinLength]] = parsePredicateIntList(`sh:minLength`, MinLength)

  private def maxLength : RDFParser[List[MaxLength]] = parsePredicateIntList(`sh:maxLength`, MaxLength)

  private def pattern: RDFParser[Pattern] = (n, rdf) => for {
    pat <- stringFromPredicate(`sh:pattern`)(n, rdf)
    flags <- stringFromPredicateOptional(`sh:flags`)(n, rdf)
  } yield Pattern(pat, flags)

  private def languageIn: RDFParser[LanguageIn] = (n, rdf) => for {
    rs <- rdfListForPredicate(`sh:languageIn`)(n, rdf)
    ls <- sequenceEither(rs.map(n => n match {
      case StringLiteral(str) => parseOk(str)
      case _ => parseFail(s"Expected to be a string literal but got $n")
    }))
  } yield LanguageIn(ls)

  private def uniqueLang: RDFParser[UniqueLang] = (n, rdf) => for {
    b <- booleanFromPredicate(`sh:uniqueLang`)(n, rdf)
  } yield UniqueLang(b)

  private def equals = parsePredicateComparison(`sh:equals`, Equals)

  private def disjoint = parsePredicateComparison(`sh:disjoint`, Disjoint)

  def lessThan = parsePredicateComparison(`sh:lessThan`, LessThan)

  def lessThanOrEquals = parsePredicateComparison(`sh:lessThanOrEquals`, LessThanOrEquals)

  def parsePredicateComparison(pred: IRI, mkComp: IRI => Component): RDFParser[Component] = (n, rdf) => for {
    p <- iriFromPredicate(pred)(n, rdf)
  } yield mkComp(p)

  def or: RDFParser[Or] = (n, rdf) => for {
    shapeNodes <- rdfListForPredicate(`sh:or`)(n, rdf)
    shapes <- mapRDFParser(shapeNodes.toList, shapeRefConst)(n, rdf)
  } yield Or(shapes)

  def and: RDFParser[And] = (n, rdf) => for {
    nodes <- rdfListForPredicate(`sh:and`)(n, rdf)
    shapes <- mapRDFParser(nodes, shapeRefConst)(n, rdf)
  } yield And(shapes)

  def xone: RDFParser[Xone] = (n, rdf) => for {
    nodes <- rdfListForPredicate(`sh:xone`)(n, rdf)
    shapes <- mapRDFParser(nodes, shapeRefConst)(n, rdf)
  } yield Xone(shapes)

  // TODO: Check if this must take into account that not is optional...
  def not: RDFParser[Not] = (n, rdf) => for {
    shapeNode <- objectFromPredicate(`sh:not`)(n, rdf)
    sref <- shapeRef(shapeNode, rdf)
  } yield Not(sref)

  def nodeComponent: RDFParser[NodeComponent] = (n, rdf) => {
    for {
      nodeShape <- objectFromPredicate(`sh:node`)(n, rdf)
      sref <- shapeRef(nodeShape, rdf)
    } yield {
      NodeComponent(sref)
    }
  }

  def qualifiedValueShape: RDFParser[QualifiedValueShape] = (n, rdf) => for {
    obj <- objectFromPredicate(`sh:qualifiedValueShape`)(n, rdf)
    sref <- shapeRef(obj, rdf)
    min <- optional(integerLiteralForPredicate(`sh:qualifiedMinCount`))(n, rdf)
    max <- optional(integerLiteralForPredicate(`sh:qualifiedMaxCount`))(n, rdf)
    disjoint <- booleanFromPredicateOptional(`sh:qualifiedValueShapesDisjoint`)(n, rdf)
  } yield QualifiedValueShape(sref, min, max, disjoint)

  def shapeRef: RDFParser[RefNode] = (n, rdf) => {
    pendingNodes = n :: pendingNodes
    parseOk(RefNode(n))
  }

  def shapeRefConst(sref: RDFNode): RDFParser[RefNode] = (_, rdf) =>
    shapeRef(sref, rdf)

  def minCount : RDFParser[List[MinCount]] = parsePredicateIntList(`sh:minCount`, MinCount)
  def maxCount : RDFParser[List[MaxCount]] = parsePredicateIntList(`sh:maxCount`, MaxCount)

  def hasValue: RDFParser[Component] = (n, rdf) => {
    logger.debug(s"Parsing hasValue on $n")
    for {
      o <- objectFromPredicate(`sh:hasValue`)(n, rdf)
      v <- {
        logger.debug(s"Object of hasValue $n = $o")
        node2Value(o)
      }
    } yield {
      logger.debug(s"Value parsed: $v")
      HasValue(v)
    }
  }

  def in: RDFParser[Component] = (n, rdf) => {
    for {
      ns <- rdfListForPredicate(`sh:in`)(n, rdf)
      vs <- convert2Values(ns.map(node2Value(_)))
    } yield In(vs)
  }

  def node2Value(n: RDFNode): Either[String, Value] = {
    n match {
      case i: IRI => parseOk(IRIValue(i))
      case l: Literal => parseOk(LiteralValue(l))
      case _ => parseFail(s"Element $n must be a IRI or a Literal to be part of sh:in")
    }
  }

  def convert2Values[A](cs: List[Either[String, A]]): Either[String, List[A]] = {
    if (cs.isEmpty)
      parseFail("The list of values associated with sh:in must not be empty")
    else {
      sequenceEither(cs)
    }
  }

  def nodeKind: RDFParser[List[Component]] = (n, rdf) => {
    for {
      os <- objectsFromPredicate(`sh:nodeKind`)(n, rdf)
      nk <- parseNodeKind(os)
    } yield {
      List(nk)
    }
  }

  def parseNodeKind(os: Set[RDFNode]): Either[String, Component] = {
    os.size match {
      case 0 => parseFail("no iriObjects of nodeKind property")
      case 1 => {
        os.head match {
          case nk: IRI => nk match {
            case `sh:IRI` => parseOk(NodeKind(IRIKind))
            case `sh:BlankNode` => parseOk(NodeKind(BlankNodeKind))
            case `sh:Literal` => parseOk(NodeKind(LiteralKind))
            case `sh:BlankNodeOrLiteral` => parseOk(NodeKind(BlankNodeOrLiteral))
            case `sh:BlankNodeOrIRI` => parseOk(NodeKind(BlankNodeOrIRI))
            case `sh:IRIOrLiteral` => parseOk(NodeKind(IRIOrLiteral))
            case x => {
              logger.error(s"incorrect value of nodeKind property $x")
              parseFail(s"incorrect value of nodeKind property $x")
            }
          }
          case x => {
            logger.error(s"incorrect value of nodeKind property $x")
            parseFail(s"incorrect value of nodeKind property $x")
          }
        }
      }
      case n => parseFail(s"iriObjects of nodeKind property > 1. $os")
    }
  }

  def noTarget: Seq[Target] = Seq()
  def noPropertyShapes: Seq[PropertyShape] = Seq()

}
