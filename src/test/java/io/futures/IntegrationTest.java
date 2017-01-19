package io.futures;

import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class IntegrationTest {
  
  private final Random random = new Random(1);
  private final Timer timer = new Timer();
  
  private Future<Integer> gen(int depth) {
    Future<Integer> f = gen();
    while(true) {
      if(depth == 0)
        break;
      else {
        f = f.flatMap(i -> gen());
        depth--;
      }
    }
    return f;
  }
  
  private Future<Integer> gen() {
    int i = random.nextInt(3);
    if(i == 1)
      return Future.value(1).delayed(10, TimeUnit.MILLISECONDS, timer);
    else if(i == 2)
      return gen().map(v -> v + 1);
    else
      return Future.value(1);
  }
  
  @Test
  public void test() throws CheckedFutureException {
//    Future<Integer> f = gen(200000);
//    f.get(1, TimeUnit.DAYS);
  }

}
