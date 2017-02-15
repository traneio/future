package io.trane.future

import io.trane.future.{ Future => JavaFuture }
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

package object scalaFlavor {
  implicit class Future[T](private val impl: JavaFuture[T]) extends AnyVal {

    def onComplete[U](f: Try[T] => U): Unit =
      impl.respond(new Responder[T] {
        def onException(ex: Throwable) = f(Failure(ex))
        def onValue(v: T) = f(Success(v))
      });

    def isCompleted: Boolean = impl.isDefined();

    def value: Option[Try[T]] =
      if (impl.isDefined())
        Some(Try(impl.get(0, TimeUnit.MILLISECONDS)))
      else
        None

    def failed: Future[Throwable] =
      transform {
        case Failure(t) => Success(t)
        case Success(v) => Failure(new NoSuchElementException("Future.failed not completed with a throwable."))
      }

    def foreach(f: Consumer[T]): Unit = impl.onSuccess(f);

    def transform[S](s: T => S, f: Throwable => Throwable): Future[S] =
      transform {
        case Success(r) => Try(s(r))
        case Failure(t) => Try(throw f(t)) // will throw fatal errors!
      }

    def transform[S](f: Try[T] => Try[S]): Future[S]

    def transformWith[S](f: Try[T] => Future[S]): Future[S]

    def map[S](f: T => S): Future[S] = impl.map(f)

    def flatMap[S](f: T => Future[S]): Future[S] = transformWith {
      case Success(s) => f(s)
      case Failure(_) => this.asInstanceOf[Future[S]]
    }

    def flatten[S](implicit ev: T <:< Future[S]): Future[S] = flatMap(ev)

    def filter(@deprecatedName('pred) p: T => Boolean): Future[T] =
      map { r => if (p(r)) r else throw new NoSuchElementException("Future.filter predicate is not satisfied") }

    final def withFilter(p: T => Boolean): Future[T] = filter(p)(executor)

    def collect[S](pf: PartialFunction[T, S]): Future[S] =
      map {
        r => pf.applyOrElse(r, (t: T) => throw new NoSuchElementException("Future.collect partial function is not defined at: " + t))
      }

    def recover[U >: T](pf: PartialFunction[Throwable, U]): Future[U] =
      transform { _ recover pf }

    def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]]): Future[U] =
      transformWith {
        case Failure(t) => pf.applyOrElse(t, (_: Throwable) => this)
        case Success(_) => this
      }

    def zip[U](that: Future[U]): Future[(T, U)] = {
      implicit val ec = internalExecutor
      flatMap { r1 => that.map(r2 => (r1, r2)) }
    }

    def zipWith[U, R](that: Future[U])(f: (T, U) => R): Future[R] =
      flatMap(r1 => that.map(r2 => f(r1, r2)))

    def fallbackTo[U >: T](that: Future[U]): Future[U] =
      if (this eq that) this
      else {
        implicit val ec = internalExecutor
        recoverWith { case _ => that } recoverWith { case _ => this }
      }

    def mapTo[S](implicit tag: ClassTag[S]): Future[S] = {
      implicit val ec = internalExecutor
      val boxedClass = {
        val c = tag.runtimeClass
        if (c.isPrimitive) Future.toBoxed(c) else c
      }
      require(boxedClass ne null)
      map(s => boxedClass.cast(s).asInstanceOf[S])
    }

    def andThen[U](pf: PartialFunction[Try[T], U]): Future[T] =
      transform {
        result =>
          try pf.applyOrElse[Try[T], Any](result, Predef.identity[Try[T]])
          catch { case NonFatal(t) => executor reportFailure t }

          result
      }
  }
}