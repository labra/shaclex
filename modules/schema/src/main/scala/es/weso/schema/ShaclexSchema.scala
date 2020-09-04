package es.weso.schema

import cats.syntax.all._
import es.weso.rdf._
import es.weso.rdf.nodes._
import es.weso.rdf.jena.RDFAsJenaModel
import es.weso.shacl.SHACLPrefixes.`owl:imports`
import es.weso.shacl.report.{AbstractResult, MsgError}
import es.weso.shacl.{Schema => ShaclSchema, _}
// import es.weso.shacl._
import es.weso.shacl.converter.{RDF2Shacl, Shacl2ShEx}
import es.weso.shacl.report.{ValidationReport, ValidationResult}
import es.weso.shacl.validator.{CheckResult, Evidence, ShapeTyping, Validator}
import es.weso.shapeMaps._
import es.weso.utils.internal.CollectionCompat._
import util._
import es.weso.typing._
import es.weso.utils.MapUtils
import cats.data.EitherT
import cats.effect._

case class ShaclexSchema(schema: ShaclSchema) extends Schema {
  override def name = "SHACLex"

  override def formats = DataFormats.formatNames ++ Seq("TREE")

  override def defaultTriggerMode = TargetDeclarations

  override def validate(rdf: RDFReader, trigger: ValidationTrigger): IO[Result] = trigger match {
    case TargetDeclarations => validateTargetDecls(rdf).map(_.addTrigger(trigger))
    case _ => IO(Result.errStr(s"Not implemented trigger ${trigger.name} for SHACL yet"))
  }

  def validateTargetDecls(rdf: RDFReader): IO[Result] = {
    val validator = Validator(schema)
    for {
     r <- validator.validateAll(rdf)
     emptyRdf <- RDFAsJenaModel.empty  
     builder <- emptyRdf.addPrefixMap(schema.pm)
     result <-  cnvResult(r, rdf, builder)
    } yield result
  }

  def cnvResult(r: CheckResult[AbstractResult, (ShapeTyping,Boolean), List[Evidence]],
                rdf: RDFReader,
                builder: RDFBuilder
               ): IO[Result] = {
    val vr: ValidationReport =
      r.result.fold(e => ValidationReport.fromError(e), r =>
        r._1.toValidationReport
      )
    for {
      eitherVR <- vr.toRDF(builder).attempt
    } yield Result(
      isValid = vr.conforms,
      message = if (vr.conforms) "Valid" else "Not valid",
      shapeMaps = r.results.map(cnvShapeTyping(_, rdf)),
      validationReport = eitherVR.leftMap(_.getMessage),
      errors = vr.results.map(cnvViolationError(_)),
      trigger = None,
      nodesPrefixMap = rdf.getPrefixMap(),
      shapesPrefixMap = schema.pm)
  }
  
  def cnvShapeTyping(t: (ShapeTyping, Boolean), rdf: RDFReader): ResultShapeMap = {
    ResultShapeMap(
      mapValues(t._1.getMap)(cnvMapShapeResult).toMap, rdf.getPrefixMap(), schema.pm)
  }

  private def cnvMapShapeResult(m: Map[Shape, TypingResult[AbstractResult, String]]): Map[ShapeMapLabel, Info] = {

    MapUtils.cnvMap(m, cnvShape, cnvTypingResult)
  }

  private def cnvShape(s: Shape): ShapeMapLabel = {
    s.id match {
      case iri: IRI => IRILabel(iri)
      case bnode: BNode => BNodeLabel(bnode)
      case _ => throw new Exception(s"cnvShape: unexpected ${s.id}")
    }
  }

  private def cnvTypingResult(t: TypingResult[AbstractResult, String]): Info = {
    import showShacl._
    import TypingResult.showTypingResult
    Info(
      status = if (t.isOK) Conformant else NonConformant,
      reason = Some(t.show)
    // TODO: Convert typing result to JSON and add it to appInfo
    )
  }

  private def cnvViolationError(v: AbstractResult): ErrorInfo = {
    val pm = schema.pm
    v match {
      case ar: MsgError => ErrorInfo(s"Error: $ar")
      case vr: ValidationResult =>
        ErrorInfo(
          pm.qualify(vr.sourceConstraintComponent) +
            " FocusNode: " + schema.pm.qualify(vr.focusNode) + " " +
            vr.message.mkString(","))
    }
  }

  /*def validateShapeMap(sm: Map[RDFNode,Set[String]], nodesStart: Set[RDFNode], rdf: RDFReader) : Result = {
    throw new Exception("Unimplemented validateShapeMap")
  }*/

  override def fromString(cs: CharSequence, format: String, 
     base: Option[String]
    ): EitherT[IO, String, Schema] = {
    for {
      rdf <- EitherT.liftF(RDFAsJenaModel.fromString(cs.toString, format, base.map(IRI(_))))
      schema <- RDF2Shacl.getShacl(rdf, true)
    } yield ShaclexSchema(schema)
  }

  private def err[A](msg:String): EitherT[IO,String, A] = EitherT.leftT[IO,A](msg)

  override def fromRDF(rdf: RDFReader): EitherT[IO, String, Schema] = for {
    eitherBuilder <- EitherT.liftF(rdf.asRDFBuilder.attempt)
    schema <- eitherBuilder match {
    case Left(_) => for {
      ts <- EitherT.liftF(rdf.triplesWithPredicate(`owl:imports`).compile.toList)
      schema <- ts.size match {
        case 0 => RDF2Shacl.getShaclReader(rdf).map(ShaclexSchema(_))
        case _ => err[ShaclexSchema](s"fromRDF: Not supported owl:imports for this kind of RDF model\nRDFReader: ${rdf}")
      }
    } yield schema
    case Right(rdfBuilder) =>
      for {
        schemaShacl <- RDF2Shacl.getShacl(rdfBuilder, true)
      } yield ShaclexSchema(schemaShacl) 
   }
  } yield schema

  override def serialize(format: String, base: Option[IRI]): IO[String] = for {
    builder <- RDFAsJenaModel.empty
    str <- if (formats.contains(format.toUpperCase))
      schema.serialize(format, base, builder)
    else IO.raiseError(new RuntimeException(s"Format $format not supported to serialize $name. Supported formats=$formats"))
  } yield str  

  override def empty: Schema = ShaclexSchema.empty

  override def shapes: List[String] = {
    schema.shapes.map(_.id).map(_.toString).toList
  }

  override def pm: PrefixMap = schema.pm

  override def convert(targetFormat: Option[String],
                       targetEngine: Option[String],
                       base: Option[IRI]
                      ): EitherT[IO,String,String] = {
   targetEngine.map(_.toUpperCase) match {
     case None => EitherT.liftF(serialize(targetFormat.getOrElse(DataFormats.defaultFormatName)))
     case Some("SHACL") | Some("SHACLEX") => {
       EitherT.liftF(serialize(targetFormat.getOrElse(DataFormats.defaultFormatName)))
     }
     case Some("SHEX") => for {
       pair <- EitherT.fromEither[IO](Shacl2ShEx.shacl2ShEx(schema)).leftMap(e => s"Error converting: $e")
       (newSchema,queryMap) = pair
       builder <- EitherT.liftF(RDFAsJenaModel.empty)
       str <- EitherT.liftF(es.weso.shex.Schema.serialize(
         newSchema,
         targetFormat.getOrElse(DataFormats.defaultFormatName),
         base,
         builder))
     } yield str
     case Some(other) => err(s"Conversion $name -> $other not implemented yet")
   }
  }

  override def info: SchemaInfo = {
    // TODO: Check if shacl schemas are well formed
    SchemaInfo(name,"SHACLex", true, List())
  }

  override def toClingo(rdf: RDFReader, shapeMap: ShapeMap)
  : EitherT[IO,String, String] = EitherT.fromEither(Left(s"Not implemented yet"))

}

object ShaclexSchema {
  def empty: ShaclexSchema = ShaclexSchema(schema = ShaclSchema.empty)

}
