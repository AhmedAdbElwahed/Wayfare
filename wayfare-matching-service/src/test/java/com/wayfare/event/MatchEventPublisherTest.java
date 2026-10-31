package com.wayfare.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

class MatchEventPublisherTest {

    StreamBridge bridge = mock(StreamBridge.class);
    MatchEventPublisher publisher = new MatchEventPublisher(bridge);

    @Test
    void driverMatchedIsKeyedByTrip() {
        when(bridge.send(eq(MatchEventPublisher.BINDING), any(Message.class))).thenReturn(true);

        publisher.driverMatched("t1", "d1");

        MatchEvent event = (MatchEvent) sent().getPayload();
        assertThat(sent().getHeaders().get(KafkaHeaders.KEY)).isEqualTo("t1");
        assertThat(event.getTripId()).isEqualTo("t1");
        assertThat(event.getDriverMatched().getDriverId()).isEqualTo("d1");
        assertThat(event.hasOccurredAt()).isTrue();
    }

    @Test
    void noDriversAvailableSetsThatOutcome() {
        when(bridge.send(eq(MatchEventPublisher.BINDING), any(Message.class))).thenReturn(true);

        publisher.noDriversAvailable("t1");

        assertThat(((MatchEvent) sent().getPayload()).getOutcomeCase())
                .isEqualTo(MatchEvent.OutcomeCase.NO_DRIVERS_AVAILABLE);
    }

    @Test
    void failedSendThrows() {
        when(bridge.send(eq(MatchEventPublisher.BINDING), any(Message.class))).thenReturn(false);

        assertThatThrownBy(() -> publisher.noDriversAvailable("t1")).isInstanceOf(IllegalStateException.class);
    }

    private Message<?> sent() {
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(bridge, org.mockito.Mockito.atLeastOnce()).send(eq(MatchEventPublisher.BINDING), captor.capture());
        return captor.getValue();
    }
}
