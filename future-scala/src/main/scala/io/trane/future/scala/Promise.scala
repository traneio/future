package io.trane.future.scala

import io.trane.future.{ Promise => JPromise, Future => JFuture }
import scala.util.{ Try, Success, Failure }
import scala.annotation.unchecked.uncheckedVariance

class Promise[T](private[trane] val underlying: JPromise[T @uncheckedVariance]) extends AnyVal {
  
  def toJava[B >: T]: JPromise[B] = underlying.asInstanceOf[JPromise[B]]

  def future: Future[T] = new Future(underlying)

  def isCompleted: Boolean = underlying.isDefined

  def complete(result: Try[T]) =
    if (tryComplete(result)) this else throw new IllegalStateException("Promise already completed.")

  def tryComplete(result: Try[T]): Boolean =
    result match {
      case Success(v)  => underlying.becomeIfEmpty(JFuture.value(v))
      case Failure(ex) => underlying.becomeIfEmpty(JFuture.exception(ex))
    }

  final def completeWith(other: Future[T]) = tryCompleteWith(other)

  def tryCompleteWith(other: Future[T]) = {
    underlying.becomeIfEmpty(other.toJava)
    this
  }

  def success(value: T) = {
    underlying.setValue(value)
    this
  }

  def trySuccess(value: T): Boolean =
    underlying.becomeIfEmpty(JFuture.value(value))

  def failure(cause: Throwable) = {
    underlying.setException(cause)
    this
  }

  def tryFailure(cause: Throwable): Boolean =
    underlying.becomeIfEmpty(JFuture.exception(cause))
}

object Promise {

  def apply[T](): Promise[T] = new Promise(JPromise.apply())

  def failed[T](exception: Throwable): Promise[T] = {
    val p = JPromise.apply[T]()
    p.setException(exception)
    new Promise(p)
  }

  def successful[T](result: T): Promise[T] = {
    val p = JPromise.apply[T]()
    p.setValue(result)
    new Promise(p)
  }

  def fromTry[T](result: Try[T]): Promise[T] =
    result match {
      case Success(v)  => successful(v)
      case Failure(ex) => failed(ex)
    }
}
