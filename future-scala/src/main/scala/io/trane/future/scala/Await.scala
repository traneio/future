package io.trane.future.scala

import scala.concurrent.duration.Duration

object Await {

  def ready[T](awaitable: Future[T], atMost: Duration) = {
    awaitable.underlying.join(toJavaDuration(atMost))
    this
  }

  def result[T](awaitable: Future[T], atMost: Duration): T =
    awaitable.underlying.get(toJavaDuration(atMost))
    
  private final def toJavaDuration(d: Duration) = 
    if(d.isFinite()) java.time.Duration.ofMillis(d.toMillis)
    else java.time.Duration.ofNanos(Long.MaxValue)
}