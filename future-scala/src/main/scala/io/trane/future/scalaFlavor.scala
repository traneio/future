package io.trane.future

import io.trane.future.{ Future => JFuture }
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.{ Function => JavaFunction }
import java.util.function.Predicate
import scala.reflect.ClassTag
import scala.collection.generic.CanBuildFrom
import java.util.Collection

object Test extends App {
  import scalaFlavor._
  //  val a = (new Future(JFuture.value(1))).flatMap { _ =>
  //    new Future(JFuture.value(1))
  //  }
  //  println(a)
}

package object scalaFlavor {

  object Future {
    private[Future] val toBoxed = Map[Class[_], Class[_]](
      classOf[Boolean] -> classOf[java.lang.Boolean],
      classOf[Byte] -> classOf[java.lang.Byte],
      classOf[Char] -> classOf[java.lang.Character],
      classOf[Short] -> classOf[java.lang.Short],
      classOf[Int] -> classOf[java.lang.Integer],
      classOf[Long] -> classOf[java.lang.Long],
      classOf[Float] -> classOf[java.lang.Float],
      classOf[Double] -> classOf[java.lang.Double],
      classOf[Unit] -> classOf[scala.runtime.BoxedUnit])

    val never: Future[Nothing] = new Future(JFuture.never())

    val unit: Future[Unit] = new Future(JFuture.value({}))

    def failed[T](exception: Throwable): Future[T] = new Future(JFuture.exception(exception))

    def successful[T](result: T): Future[T] = new Future(JFuture.value(result))

    def fromTry[T](result: Try[T]): Future[T] =
      result match {
        case Success(value) => successful(value)
        case Failure(exception) => failed(exception)
      }

    def apply[T](body: => T): Future[T] = new Future(JFuture.apply(() => body))

    private[this] def toJList[A, M[X] <: TraversableOnce[X]](in: M[Future[A]]) = {
      import scala.collection.JavaConverters._
      in.toSeq.asJava.asInstanceOf[java.util.List[JFuture[A]]]
    }

    def sequence[A, M[X] <: TraversableOnce[X]](in: M[Future[A]])(implicit cbf: CanBuildFrom[M[Future[A]], A, M[A]]): Future[M[A]] = {
      new Future(JFuture.collect(toJList(in))).map { jList =>
        val builder = cbf()
        val size = jList.size
        var i = 0
        while (i < size)
          builder += jList.get(i)
        builder.result()
      }
    }

    def firstCompletedOf[T](futures: TraversableOnce[Future[T]]): Future[T] = {
      import scala.collection.JavaConverters._
      new Future(JFuture.firstCompletedOf(toJList(futures)));
    }

    def find[T](futures: scala.collection.immutable.Iterable[Future[T]])(p: T => Boolean): Future[Option[T]] = {
      def searchNext(i: Iterator[Future[T]]): Future[Option[T]] =
        if (!i.hasNext) successful[Option[T]](None)
        else {
          i.next().transformWith {
            case Success(r) if p(r) => successful(Some(r))
            case other => searchNext(i)
          }
        }
      searchNext(futures.iterator)
    }

    def foldLeft[T, R](futures: scala.collection.immutable.Iterable[Future[T]])(zero: R)(op: (R, T) => R): Future[R] =
      foldNext(futures.iterator, zero, op)

    private[this] def foldNext[T, R](i: Iterator[Future[T]], prevValue: R, op: (R, T) => R): Future[R] =
      if (!i.hasNext) successful(prevValue)
      else i.next().flatMap { value => foldNext(i, op(prevValue, value), op) }

    def reduceLeft[T, R >: T](futures: scala.collection.immutable.Iterable[Future[T]])(op: (R, T) => R): Future[R] = {
      val i = futures.iterator
      if (!i.hasNext) failed(new NoSuchElementException("reduceLeft attempted on empty collection"))
      else i.next() flatMap { v => foldNext(i, v, op) }
    }

    def traverse[A, B, M[X] <: TraversableOnce[X]](in: M[A])(fn: A => Future[B])(implicit cbf: CanBuildFrom[M[A], B, M[B]]): Future[M[B]] =
      JFuture.collect(toJList(in.map(fn))).map { jList =>
        val builder = cbf()
        jList.forEach(builder += _)
        builder.result()
      }
  }
  class Future[+T](private val _impl: JFuture[_ <: T]) extends AnyVal {

    private[this] def impl = _impl.asInstanceOf[JFuture[T]]

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
      new Future(
        impl.transformWith(new Transformer[T, JFuture[Throwable]] {
          override def onValue(value: T) = JFuture.exception(new NoSuchElementException("Future.failed not completed with a throwable."))
          override def onException(ex: Throwable) = JFuture.value(ex)
        }))

    def foreach[U](f: T => U): Unit = impl.onSuccess(v => f(v))

    def transform[S](s: T => S, f: Throwable => Throwable): Future[S] =
      new Future(
        impl.transformWith(new Transformer[T, JFuture[S]] {
          override def onValue(value: T) = JFuture.value(s(value))
          override def onException(ex: Throwable) = JFuture.exception(f(ex))
        }))

    def transform[S](f: Try[T] => Try[S]): Future[S] = {
      def toJFuture[T](t: Try[T]): JFuture[T] =
        t match {
          case Success(r) => JFuture.value(r)
          case Failure(ex) => JFuture.exception(ex)
        }
      new Future[S](
        impl.transformWith(new Transformer[T, JFuture[S]] {
          override def onValue(value: T) = toJFuture(f(Success(value)))
          override def onException(ex: Throwable) = toJFuture(f(Failure(ex)))
        }))
    }

    def transformWith[S](f: Try[T] => Future[S]): Future[S] =
      new Future(
        impl.transformWith(new Transformer[T, JFuture[S]] {
          override def onValue(value: T) = f(Success(value)).asInstanceOf[JFuture[S]]
          override def onException(ex: Throwable) = f(Failure(ex)).asInstanceOf[JFuture[S]]
        }))

    def map[S](f: T => S): Future[S] = new Future(impl.map(v => f(v)))

    def flatMap[S](f: T => Future[S]): Future[S] =
      new Future[S](impl.flatMap[S](v => f(v).asInstanceOf[JFuture[S]]))

    def flatten[S](implicit ev: T <:< Future[S]): Future[S] =
      new Future(JFuture.flatten(impl.asInstanceOf[JFuture[JFuture[S]]]))

    def filter(p: T => Boolean): Future[T] =
      new Future(impl.filter(v => p(v)))

    final def withFilter(p: T => Boolean): Future[T] = filter(p)

    def collect[S](pf: PartialFunction[T, S]): Future[S] =
      new Future(impl.map {
        r => pf.applyOrElse(r, (t: T) => throw new NoSuchElementException("Future.collect partial function is not defined at: " + t))
      })

    def recover[U >: T](pf: PartialFunction[Throwable, U]): Future[U] =
      new Future(
        impl.asInstanceOf[JFuture[U]].rescue { ex =>
          if (pf.isDefinedAt(ex)) JFuture.value(pf(ex))
          else impl.asInstanceOf[JFuture[U]]
        })

    def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]]): Future[U] =
      new Future(
        impl.asInstanceOf[JFuture[U]].rescue { ex =>
          if (pf.isDefinedAt(ex)) pf(ex).asInstanceOf[JFuture[U]]
          else impl.asInstanceOf[JFuture[U]]
        })

    def zip[U](that: Future[U]): Future[(T, U)] =
      new Future(impl.biMap[U, (T, U)](that.asInstanceOf[JFuture[U]], (a, b) => (a, b)))

    def zipWith[U, R](that: Future[U])(f: (T, U) => R): Future[R] =
      new Future(impl.biMap[U, R](that.asInstanceOf[JFuture[U]], (a, b) => f(a, b)))

    def fallbackTo[U >: T](that: Future[U]): Future[U] =
      new Future(impl.fallbackTo(that.asInstanceOf[JFuture[T]]))

    def mapTo[S](implicit tag: ClassTag[S]): Future[S] = {
      val boxedClass = {
        val c = tag.runtimeClass
        if (c.isPrimitive) Future.toBoxed(c) else c
      }
      require(boxedClass ne null)
      map(s => boxedClass.cast(s).asInstanceOf[S])
    }

    def andThen[U](pf: PartialFunction[Try[T], U]): Future[T] =
      new Future(impl.respond(new Responder[T] {
        def onException(ex: Throwable) = pf.applyOrElse[Try[T], Any](Failure(ex), Predef.identity[Try[T]])
        def onValue(v: T) = pf.applyOrElse[Try[T], Any](Success(v), Predef.identity[Try[T]])
      }));
  }
}