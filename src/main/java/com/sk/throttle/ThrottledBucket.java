package com.sk.throttle;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class ThrottledBucket<V> {

  private final DelayQueue<DelayedMessage<V>> delayQueue = new DelayQueue<>();
  private final int maxRate;
  private final int minTime;
  private int counter = 0;
  private Lock lock = new ReentrantLock();

  // holds time of last action (past or future!)
  private long lastScheduledAction = System.currentTimeMillis();

  public ThrottledBucket(int maxRate, int minTimeMillis) {
    if (maxRate <= 0 || minTimeMillis <= 0) {
      throw new IllegalArgumentException(
          String.format("Invalid rate or time %s,%s", maxRate, minTimeMillis));
    }
    this.maxRate = maxRate;
    this.minTime = minTimeMillis;
    this.counter = this.maxRate;
    log.info("initialized with maxRate {}  per millis {}", maxRate, minTimeMillis);
  }

  public void reset() {
    lastScheduledAction = System.currentTimeMillis();
  }

  public boolean tryConsume(V task) {
    try {
      lock.lock();
      long curTime = System.currentTimeMillis();
      long timeLeft = lastScheduledAction + minTime - curTime;
      log.info( "Time left "+ timeLeft + ", permitted count " + (counter-1));
      if (timeLeft <= 0 && counter <= 0) {
        lastScheduledAction = curTime;
        counter = this.maxRate;
      }
      // If needed, wait for our time
      if (timeLeft > 0 && counter > 0) {
        counter--;
        return true;
      } else {
        return !delayQueue.add(new DelayedMessage<>(task, timeLeft));
      }
    } finally{
      lock.unlock();
    }
  }

  public List<V> retrieve() {
    List<V> res = new ArrayList<>();
    for (DelayedMessage<V> v : delayQueue) {
      res.add(v.getValue());
    }
    return res;
  }

  private static class DelayedMessage<V> implements Delayed {

    private final V value;
    private final long startTime;

    public DelayedMessage(V value, long delayInMilliseconds) {
      this.value = value;
      this.startTime = System.currentTimeMillis() + delayInMilliseconds;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      long diff = startTime - System.currentTimeMillis();
      return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      long l = this.startTime - ((ThrottledBucket.DelayedMessage<V>) o).startTime;
      return (int) l;
    }

    public V getValue() {
      return value;
    }
  }
}
