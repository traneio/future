package io.futures;

import java.util.concurrent.CountDownLatch;

public class CountDownOnRunLatch extends CountDownLatch implements Runnable {

  public CountDownOnRunLatch(int count) {
    super(count);
  }

  @Override
  public void run() {
    super.countDown();
  }
}
