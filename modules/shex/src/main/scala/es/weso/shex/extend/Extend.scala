package es.weso.shex.extend
import cats._
import cats.data._
import cats.implicits._

trait Extend {

  def extendCheckingVisited[S,E,Label](s: S,
                   finder: Label => Either[String,S],
                   extend: S => Option[List[Label]],
                   combineExpr: (E, E) => E,
                   expr: S => Option[E]): Either[String, Option[E]] = {
    type Visited[A] = State[List[Label], A]
    def getVisited: Visited[List[Label]] = State.get
    def addVisited(x: Label): Visited[Unit] = {
      def fn(ls: List[Label]): List[Label] = x :: ls
      State.modify[List[Label]](fn)
    }
    def err(msg: String): Visited[Either[String, Option[E]]] = ok {
      val e: Either[String, Option[E]] = Left(msg)
      e
    }

    def ok[A](x: A): Visited[A] = StateT.pure(x)

    def combine(e1: Option[E], e2: Option[E]): Option[E] = (e1, e2) match {
      case (None, None)         => None
      case (Some(v1), None)     => Some(v1)
      case (None, Some(v2))     => Some(v2)
      case (Some(v1), Some(v2)) => Some(combineExpr(v1, v2))
    }

    type Result = Either[String,Option[E]]

    def flattenExprAux(s: S): Visited[Result] = extend(s) match {
      case None => ok(Right(expr(s)))
      case Some(exprs) => {
        val zero: Result = Right(None)
        def comb(r: Result, x: Label): Visited[Result] = {
          // This needs to walk through a ShapeExpr and only addVisited for ShapeReferences.
          // println(s"comb, x=$x, r=$r")
          for {
            visited <- getVisited
            v <- if (visited contains x) {
              ok(r) // Circular dependency
            } else
              finder(x) match {
                case Left(e) => err(e)
                case Right(shape) =>
                  for {
                    _  <- addVisited(x)
                    ef <- flattenExprAux(shape)
                  } yield
                    for {
                      v1 <- ef
                      v2 <- r
                    } yield {
                      combine(v1, v2)
                    }
              }
          } yield v
        }
        for { r <- Foldable[List].foldM(exprs, zero)(comb)
        } yield {
          r.map(combine(_,expr(s)))
        }
      }
    }
    val (visited, e) = flattenExprAux(s).run(List()).value
    e
  }
}

