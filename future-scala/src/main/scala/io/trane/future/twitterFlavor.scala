package io.trane.future

import io.trane.future.{ Future => JavaFuture }

package object twitterFlavor {
  implicit class Future[T](private val impl: JavaFuture[T]) extends AnyVal {
    
  }
}