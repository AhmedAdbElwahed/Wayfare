package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wayfare.domain.ApprovedDriver;
import com.wayfare.grpc.driver.BatchGetDriversRequest;
import com.wayfare.grpc.driver.BatchGetDriversResponse;
import com.wayfare.grpc.driver.DriverResponse;
import com.wayfare.grpc.driver.DriverServiceGrpc;
import com.wayfare.grpc.driver.DriverStatus;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

/** Real gRPC wire path (in-process) against a fake Driver Service. */
class DriverDirectoryTest {

    final List<DriverResponse> known = new ArrayList<>();
    final List<BatchGetDriversRequest> requests = new ArrayList<>();
    Status failWith;

    Server server;
    ManagedChannel channel;
    DriverDirectory directory;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new FakeDriverService()).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        directory = new DriverDirectory(DriverServiceGrpc.newBlockingStub(channel), TestProperties.defaults());
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void returnsOnlyApprovedDriversWithTheirRating() {
        String approved = id(), unrated = id(), suspended = id(), unknown = id();
        known.add(driver(approved, DriverStatus.APPROVED, "4.85"));
        known.add(driver(unrated, DriverStatus.APPROVED, null));
        known.add(driver(suspended, DriverStatus.SUSPENDED, "5.00"));

        Map<String, ApprovedDriver> result = directory.approved(List.of(approved, unrated, suspended, unknown));

        assertThat(result).containsOnlyKeys(approved, unrated);
        assertThat(result.get(approved).ratingAvg()).isEqualTo(4.85);
        assertThat(result.get(unrated).ratingAvg()).isNull();
    }

    @Test
    void unparseableRatingIsTreatedAsMissing() {
        String d = id();
        known.add(driver(d, DriverStatus.APPROVED, "n/a"));

        assertThat(directory.approved(List.of(d)).get(d).ratingAvg()).isNull();
    }

    @Test
    void nonUuidIdsAreDroppedBeforeTheCall() {
        String d = id();
        known.add(driver(d, DriverStatus.APPROVED, "4.0"));

        assertThat(directory.approved(List.of("not-a-uuid", d))).containsOnlyKeys(d);
        assertThat(requests.get(0).getDriverIdsList()).containsExactly(d);
    }

    @Test
    void emptyInputMakesNoCall() {
        assertThat(directory.approved(List.of("junk"))).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void serverErrorPropagates() {
        failWith = Status.UNAVAILABLE;

        assertThatThrownBy(() -> directory.approved(List.of(id())))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }

    // ---- helpers ----

    private class FakeDriverService extends DriverServiceGrpc.DriverServiceImplBase {
        @Override
        public void batchGetDrivers(BatchGetDriversRequest request,
                StreamObserver<BatchGetDriversResponse> out) {
            requests.add(request);
            if (failWith != null) {
                out.onError(failWith.asRuntimeException());
                return;
            }
            BatchGetDriversResponse.Builder response = BatchGetDriversResponse.newBuilder();
            known.stream().filter(d -> request.getDriverIdsList().contains(d.getDriverId()))
                    .forEach(response::addDrivers);
            out.onNext(response.build());
            out.onCompleted();
        }
    }

    private static DriverResponse driver(String id, DriverStatus status, String rating) {
        DriverResponse.Builder b = DriverResponse.newBuilder().setDriverId(id).setStatus(status);
        if (rating != null) {
            b.setRatingAvg(rating);
        }
        return b.build();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
