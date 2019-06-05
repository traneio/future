package io.trane.future.scala

import scala.concurrent.duration.Duration

import org.junit.Assert.assertEquals
import org.junit.Test

class ExceptionFutureTest {
  private def get[T](future: Future[T]): T = Await.result(future, Duration.Zero)

  val ex = new TestException

  /*** recover ***/

  @Test
  def recoverAll(): Unit = {
    val future = Future.failed(ex).recover {
      case t: Throwable =>
        assertEquals(ex, t)
        1
    }
    assertEquals(1, get(future))
  }

  @Test(expected = classOf[TestException])
  def recoverPartial(): Unit = {
    val future = Future.failed(ex).recover {
      case t: IllegalStateException => 1
    }
    assertEquals(1, get(future))
  }

  @Test(expected = classOf[IllegalStateException])
  def recoverFailed(): Unit = {
    val future = Future.failed(ex).recover {
      case _: Throwable => throw new IllegalStateException
    }
    assertEquals(1, get(future))
  }

  /*** recoverWith ***/

  @Test
  def recoverWithAll(): Unit = {
    val future = Future.failed(ex).recoverWith {
      case t: Throwable =>
        assertEquals(ex, t)
        Future.successful(1)
    }
    assertEquals(1, get(future))
  }

  @Test(expected = classOf[IllegalStateException])
  def recoverWithFailed(): Unit = {
    val future = Future.failed(ex).recoverWith {
      case _: Throwable => throw new IllegalStateException
    }
    assertEquals(1, get(future))
  }
}
