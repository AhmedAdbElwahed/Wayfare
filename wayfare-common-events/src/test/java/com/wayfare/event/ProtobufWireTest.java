package com.wayfare.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Message;

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;

/**
 * Round-trips the events through the real Confluent serializer/deserializer
 * against Confluent's in-memory registry ({@code mock://}). Proves the
 * generated classes, the protobuf runtime and the serializer agree, without a
 * broker or registry container.
 */
class ProtobufWireTest {

    private static final String SCOPE = "wire-test";
    private static final String URL = "mock://" + SCOPE;

    private KafkaProtobufSerializer<Message> serializer;

    @BeforeEach
    void setUp() {
        serializer = new KafkaProtobufSerializer<>();
        serializer.configure(Map.of("schema.registry.url", URL), false);
    }

    @AfterEach
    void tearDown() {
        MockSchemaRegistry.dropScope(SCOPE);
    }

    private <T extends Message> T roundTrip(String topic, Message event, Class<T> type) {
        try (var deserializer = new KafkaProtobufDeserializer<T>()) {
            deserializer.configure(Map.of(
                    "schema.registry.url", URL,
                    "specific.protobuf.value.type", type.getName()), false);
            return deserializer.deserialize(topic, serializer.serialize(topic, event));
        }
    }

    @Test
    void accountRegisteredSurvivesTheWire() {
        var event = AccountRegisteredEvent.newBuilder()
                .setAccountId(UUID.randomUUID().toString())
                .setEmail("a@b.c")
                .setRole("DRIVER")
                .setRegisteredAt(ProtoTimes.toTimestamp(Instant.parse("2026-10-14T12:00:00.123456789Z")))
                .build();

        AccountRegisteredEvent back = roundTrip("account.events", event, AccountRegisteredEvent.class);

        assertThat(back).isEqualTo(event);
        assertThat(ProtoTimes.toInstant(back.getRegisteredAt())).isEqualTo(Instant.parse("2026-10-14T12:00:00.123456789Z"));
    }

    @Test
    void driverStatusChangedKeepsOptionalReason() {
        var suspended = DriverStatusChangedEvent.newBuilder()
                .setDriverId(UUID.randomUUID().toString())
                .setStatus("SUSPENDED")
                .setReason("document_expired")
                .setOccurredAt(ProtoTimes.toTimestamp(Instant.now()))
                .build();
        var approved = suspended.toBuilder().setStatus("APPROVED").clearReason().build();

        var backSuspended = roundTrip("driver.events", suspended, DriverStatusChangedEvent.class);
        var backApproved = roundTrip("driver.events", approved, DriverStatusChangedEvent.class);

        assertThat(backSuspended.hasReason()).isTrue();
        assertThat(backSuspended.getReason()).isEqualTo("document_expired");
        assertThat(backApproved.hasReason()).isFalse();
    }
}
