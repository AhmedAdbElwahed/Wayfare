package com.wayfare.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wayfare.domain.PaymentMethod;
import com.wayfare.domain.Profile;
import com.wayfare.exception.ProfileNotFoundException;
import com.wayfare.grpc.rider.GetProfileRequest;
import com.wayfare.grpc.rider.ProfileResponse;
import com.wayfare.grpc.rider.RiderServiceGrpc;
import com.wayfare.grpc.rider.ValidatePaymentMethodRequest;
import com.wayfare.grpc.rider.ValidationResponse;
import com.wayfare.repository.PaymentMethodRepository;
import com.wayfare.service.ProfileService;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

/** Exercises the real gRPC wire path (in-process) with the service layer mocked. */
class RiderGrpcServiceTest {

    ProfileService profiles = mock(ProfileService.class);
    PaymentMethodRepository payments = mock(PaymentMethodRepository.class);
    Server server;
    ManagedChannel channel;
    RiderServiceGrpc.RiderServiceBlockingStub stub;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new RiderGrpcService(profiles, payments)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = RiderServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void getProfileReturnsProfile() {
        UUID id = UUID.randomUUID();
        when(profiles.getProfile(id)).thenReturn(new Profile(id, "Sam", "+100", null, "en"));

        ProfileResponse r = stub.getProfile(GetProfileRequest.newBuilder().setRiderId(id.toString()).build());

        assertThat(r.getName()).isEqualTo("Sam");
        assertThat(r.getLocale()).isEqualTo("en");
        assertThat(r.hasPhotoUrl()).isFalse();
    }

    @Test
    void getProfileUnknownIsNotFound() {
        UUID id = UUID.randomUUID();
        when(profiles.getProfile(id)).thenThrow(new ProfileNotFoundException(id));

        assertThat(codeOf(() -> stub.getProfile(GetProfileRequest.newBuilder().setRiderId(id.toString()).build())))
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void badUuidIsInvalidArgument() {
        assertThat(codeOf(() -> stub.getProfile(GetProfileRequest.newBuilder().setRiderId("nope").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.validatePaymentMethod(
                ValidatePaymentMethodRequest.newBuilder().setRiderId("").build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void validWhenProfileCompleteAndDefaultMethodExists() {
        UUID id = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        when(profiles.getProfile(id)).thenReturn(new Profile(id, "Sam", "+100", null, "en"));
        when(payments.findByUserId(id)).thenReturn(List.of(
                new PaymentMethod(UUID.randomUUID(), id, "tok-a", "visa", "1111", false, 0L),
                new PaymentMethod(pmId, id, "tok-secret", "visa", "4242", true, 0L)));

        ValidationResponse r = validate(id);

        assertThat(r.getValid()).isTrue();
        assertThat(r.getPaymentMethodId()).isEqualTo(pmId.toString());
        assertThat(r.toString()).doesNotContain("tok-secret");
    }

    @Test
    void invalidReasons() {
        UUID incomplete = UUID.randomUUID();
        when(profiles.getProfile(incomplete)).thenReturn(new Profile(incomplete));
        assertThat(validate(incomplete).getReason()).isEqualTo("PROFILE_INCOMPLETE");

        UUID noMethod = UUID.randomUUID();
        when(profiles.getProfile(noMethod)).thenReturn(new Profile(noMethod, "Sam", "+1", null, "en"));
        when(payments.findByUserId(noMethod)).thenReturn(List.of());
        assertThat(validate(noMethod).getReason()).isEqualTo("NO_DEFAULT_METHOD");

        UUID unknown = UUID.randomUUID();
        when(profiles.getProfile(unknown)).thenThrow(new ProfileNotFoundException(unknown));
        ValidationResponse r = validate(unknown);
        assertThat(r.getValid()).isFalse();
        assertThat(r.getReason()).isEqualTo("PROFILE_NOT_FOUND");
    }

    private ValidationResponse validate(UUID id) {
        return stub.validatePaymentMethod(ValidatePaymentMethodRequest.newBuilder().setRiderId(id.toString()).build());
    }

    private static Status.Code codeOf(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
        throw new AssertionError("expected a gRPC error");
    }
}
