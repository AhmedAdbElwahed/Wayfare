package com.wayfare.grpc;

import java.util.UUID;

import org.springframework.grpc.server.service.GrpcService;

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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

/**
 * Internal, service-to-service API (contract: wayfare-common-grpc, rider.proto).
 * Deliberately returns verdicts and display data only; the payment provider
 * token never leaves this service.
 */
@GrpcService
@RequiredArgsConstructor
public class RiderGrpcService extends RiderServiceGrpc.RiderServiceImplBase {

    static final String PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";
    static final String PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
    static final String NO_DEFAULT_METHOD = "NO_DEFAULT_METHOD";

    private final ProfileService profiles;
    private final PaymentMethodRepository payments;

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> out) {
        UUID riderId = parse(request.getRiderId(), out);
        if (riderId == null) {
            return;
        }
        try {
            out.onNext(toResponse(profiles.getProfile(riderId)));
            out.onCompleted();
        } catch (ProfileNotFoundException e) {
            out.onError(Status.NOT_FOUND.withDescription("rider not found").asRuntimeException());
        }
    }

    @Override
    public void validatePaymentMethod(ValidatePaymentMethodRequest request, StreamObserver<ValidationResponse> out) {
        UUID riderId = parse(request.getRiderId(), out);
        if (riderId == null) {
            return;
        }
        ValidationResponse response;
        try {
            Profile profile = profiles.getProfile(riderId);
            if (profile.getName() == null) {
                response = invalid(PROFILE_INCOMPLETE);
            } else {
                response = payments.findByUserId(riderId).stream()
                        .filter(PaymentMethod::isDefault)
                        .findFirst()
                        .map(pm -> ValidationResponse.newBuilder().setValid(true)
                                .setPaymentMethodId(pm.getId().toString()).build())
                        .orElseGet(() -> invalid(NO_DEFAULT_METHOD));
            }
        } catch (ProfileNotFoundException e) {
            response = invalid(PROFILE_NOT_FOUND);
        }
        out.onNext(response);
        out.onCompleted();
    }

    private static ValidationResponse invalid(String reason) {
        return ValidationResponse.newBuilder().setValid(false).setReason(reason).build();
    }

    private static ProfileResponse toResponse(Profile p) {
        ProfileResponse.Builder b = ProfileResponse.newBuilder()
                .setRiderId(p.getId().toString())
                .setName(nullToEmpty(p.getName()))
                .setPhone(nullToEmpty(p.getPhone()))
                .setLocale(nullToEmpty(p.getLocale()));
        if (p.getPhotoUrl() != null) {
            b.setPhotoUrl(p.getPhotoUrl());
        }
        return b.build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Returns null after failing the call with INVALID_ARGUMENT. */
    private static UUID parse(String raw, StreamObserver<?> out) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("rider_id must be a UUID").asRuntimeException());
            return null;
        }
    }
}
