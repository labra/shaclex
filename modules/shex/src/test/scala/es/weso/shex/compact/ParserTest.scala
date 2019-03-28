package es.weso.shex.compact

import es.weso.json.JsonTest
import es.weso.rdf.nodes.IRI
import es.weso.shex._
import org.scalatest.{EitherValues, FunSpec, Matchers}

class ParserTest extends FunSpec with JsonTest with Matchers with EitherValues {

  describe("ShEx Parser test") {

    shouldParse(s"<S> {}", None,
      Schema(IRI(""),None,None,None,None,
        Some(List(Shape(Some(IRILabel(IRI("S"))),None,Some(false),None,None,None,None,None))),None,List())
    )

/*    shouldParse(s"<S> extends <T> { }", None,
      Schema.empty.addShape(Shape.empty.copy(
        id = Some(IRILabel(IRI("S"))),
        closed = Some(false),
        _extends=Some(List(IRILabel(IRI("T")))))
      )
    ) */

    def shouldParse(str:String, base: Option[String], expected: Schema): Unit = {
      it(s"Should parse $str and obtain $expected") {
        Parser.parseSchema(str, base.map(IRI(_))) match {
          case Left(e) => fail(s"Failed to parse with error: $e")
          case Right(parsedSchema) => {
            info(s"Parsed as $parsedSchema")
            parsedSchema should be(expected)
          }
        }
      }
    }
  }
}
