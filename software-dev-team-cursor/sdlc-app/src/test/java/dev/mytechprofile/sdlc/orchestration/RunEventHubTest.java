package dev.mytechprofile.sdlc.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.mytechprofile.sdlc.domain.StepEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunEventHubTest {

    private static final StepEvent EVENT = new StepEvent("developer", "completed", "in", "out", 12);

    @Test
    void publishDeliversToSubscribersOfThatRun() {
        RunEventHub hub = new RunEventHub();
        List<StepEvent> received = new ArrayList<>();
        hub.subscribe("run-1", received::add);
        hub.subscribe("run-2", event -> {
            throw new AssertionError("other run must not receive events");
        });

        hub.publish("run-1", EVENT);

        assertThat(received).containsExactly(EVENT);
    }

    @Test
    void publishSurvivesFailingSubscriberAndKeepsHealthyOnes() {
        RunEventHub hub = new RunEventHub();
        List<StepEvent> healthy = new ArrayList<>();
        hub.subscribe("run-1", event -> {
            throw new IllegalStateException("ResponseBodyEmitter has already completed");
        });
        hub.subscribe("run-1", healthy::add);

        assertThatCode(() -> hub.publish("run-1", EVENT)).doesNotThrowAnyException();

        assertThat(healthy).containsExactly(EVENT);
    }

    @Test
    void failingSubscriberIsUnsubscribedAfterFirstFailure() {
        RunEventHub hub = new RunEventHub();
        List<StepEvent> attempts = new ArrayList<>();
        hub.subscribe("run-1", event -> {
            attempts.add(event);
            throw new IllegalStateException("ResponseBodyEmitter has already completed");
        });

        hub.publish("run-1", EVENT);
        hub.publish("run-1", EVENT);

        assertThat(attempts).hasSize(1);
    }

    @Test
    void closingSubscriptionStopsDelivery() throws Exception {
        RunEventHub hub = new RunEventHub();
        List<StepEvent> received = new ArrayList<>();
        AutoCloseable subscription = hub.subscribe("run-1", received::add);

        subscription.close();
        hub.publish("run-1", EVENT);

        assertThat(received).isEmpty();
    }
}
