package com.voidfemme.sacredcows.util;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class TickCounter {
  public static final long CLEANUP_INTERVAL_TICKS = 600; // 30 seconds
  public static final long COW_INTERVAL_TICKS = 100; // 5 seconds

  private long serverTickCounter = 0;

  private static class IntervalCallback {
    final long interval;
    final Runnable callback;
    long counter = 0;

    IntervalCallback(long interval, Runnable callback) {
      this.interval = interval;
      this.callback = callback;
    }
  }

  private final List<IntervalCallback> callbacks = new ArrayList<>();

  public void registerEventHandlers() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          this.serverTickCounter += 1;
          for (IntervalCallback ic : callbacks) {
            ic.counter += 1;
            if (ic.counter >= ic.interval) {
              ic.counter = 0;
              ic.callback.run();
            }
          }
        });
  }

  public void registerIntervalCallback(long intervalTicks, Runnable callback) {
    callbacks.add(new IntervalCallback(intervalTicks, callback));
  }

  public long getServerTickCounter() {
    return serverTickCounter;
  }
}
