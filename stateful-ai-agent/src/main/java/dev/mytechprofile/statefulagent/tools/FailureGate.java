package dev.mytechprofile.statefulagent.tools;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class FailureGate {

    private final AtomicInteger remaining = new AtomicInteger();

    public void failNext(int times) {
        this.remaining.set(times);
    }

    public boolean shouldFail() {
        return this.remaining.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
    }

    public void reset() {
        this.remaining.set(0);
    }
}
