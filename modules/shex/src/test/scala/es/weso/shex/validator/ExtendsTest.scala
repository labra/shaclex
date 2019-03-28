package es.weso.shex.validator

class ExtendsTest extends ShouldValidateShapeMap {

  describe("Simple Extends") {

      {
        val rdf  =
          """|prefix : <http://e#>
             |:x :p 1, 3 .""".stripMargin
        val shex =
          """|prefix : <http://e#>
             |:B { :p [1 2] }
             |:A extends @:B {
             | :p [3 4]
             |}""".stripMargin
//        shouldValidateWithShapeMap(rdf, shex, "<x>@<B>", "<x>@!<B>")
        // TODO: The following test result is wrong
        // It should return that :x conforms to :A (:x@:A)
        // We keep the test negative until we repair the algorithm
        shouldValidateWithShapeMap(rdf, shex, ":x@:A", ":x@!:A")
      }

    {
      val rdf  =
        """|prefix : <http://e#>
           |:x :p 2, 3 .""".stripMargin
      val shex =
        """|prefix : <http://e#>
           |:B { :p [1 2] } AND { :p [3 4] }
           |:A extends @:B {
           | :p [2 3]
           |}""".stripMargin
      //        shouldValidateWithShapeMap(rdf, shex, "<x>@<B>", "<x>@!<B>")
      shouldValidateWithShapeMap(rdf, shex, ":x@:A", ":x@!:A")
    }
/*  Not implemented yet AND  {
      val rdf  =
        """|prefix : <http://e#>
           |:x :p 2, 3 .""".stripMargin
      val shex =
        """|prefix : <http://e#>
           |:B { :p [1 2] } AND { :p [2 3] }
           |:A extends :B {
           | :p [2 3]
           |}""".stripMargin
      //        shouldValidateWithShapeMap(rdf, shex, "<x>@<B>", "<x>@!<B>")
      shouldValidateWithShapeMap(rdf, shex, ":x@:A", ":x@:A")
    } */


    }

  }
