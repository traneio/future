package io.trane.future

import io.trane.future.{ Future => JFuture, Promise => JPromise }

package object scala {
  implicit class toScalaPromise[T](val p: JPromise[T]) extends AnyVal {
    def toScala: Promise[T] = new Promise(p)
  }
  implicit class toScalaFuture[T](val fut: JFuture[T]) extends AnyVal {
    def toScala: Future[T] = new Future(fut)
  }

  implicit class toJavaPromise[T](val p: Promise[T]) {
    def toJava: JPromise[T] = p.underlying
  }
  implicit class toJavaFuture[T](val fut: Future[T]) {
    def toJava: JFuture[T] = fut.underlying
  }
}