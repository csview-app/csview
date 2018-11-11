package net.kothar.csview;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Timer {
    long first;
    long last;
    String lastTask;

    public Map<String, Long> tasks = new HashMap<>();

    public Timer(String task) {
        lastTask = task;
        first = System.nanoTime();
        last = first;
    }

    public void subTask(String task) {
        endLastTask();
        lastTask = task;
    }

    public void stop() {
        endLastTask();
    }

    private void endLastTask() {
        long now = System.nanoTime();
        if (lastTask != null) {
            long elapsed = now - last;
            tasks.compute(lastTask, (t, value) -> {
                if (value == null) {
                    return elapsed;
                }
                return value + elapsed;
            });
        }
        last = now;
    }

    public Duration total() {
        return Duration.ofNanos(last - first);
    }
}
