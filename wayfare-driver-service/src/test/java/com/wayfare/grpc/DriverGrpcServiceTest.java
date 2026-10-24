package com.wayfare.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wayfare.domain.Driver;
import com.wayfare.domain.DriverStatus;
import com.wayfare.domain.RideType;
import com.wayfare.domain.Vehicle;
import com.wayfare.grpc.driver.BatchGetDriversRequest;
import com.wayfare.grpc.driver.BatchGetDriversResponse;
import com.wayfare.grpc.driver.DriverResponse;
import com.wayfare.grpc.driver.DriverServiceGrpc;
import com.wayfare.grpc.driver.GetDriverRequest;
import com.wayfare.repository.DriverRepository;
import com.wayfare.repository.VehicleRepository;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

/** Exercises the real gRPC wire path (in-process) with the repositories mocked. */
class DriverGrpcServiceTest {

    DriverRepository drivers = mock(DriverRepository.class);
    VehicleRepository vehicles = mock(VehicleRepository.class);
    Server server;
    ManagedChannel channel;
    DriverServiceGrpc.DriverServiceBlockingStub stub;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new DriverGrpcService(drivers, vehicles)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = DriverServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    // ---- getDriver ----

    @Test
    void getDriverReturnsAllFields() {
        Driver d = driver(UUID.randomUUID(), DriverStatus.APPROVED);
        d.setRatingAvg(new BigDecimal("4.85"));
        when(drivers.findById(d.getId())).thenReturn(java.util.Optional.of(d));
        when(vehicles.findByDriverId(d.getId())).thenReturn(java.util.Optional.of(vehicle(d.getId(), RideType.COMFORT)));

        DriverResponse r = stub.getDriver(get(d.getId().toString()));

        assertThat(r.getDriverId()).isEqualTo(d.getId().toString());
        assertThat(r.getStatus()).isEqualTo(com.wayfare.grpc.driver.DriverStatus.APPROVED);
        assertThat(r.getName()).isEqualTo("Sam");
        assertThat(r.getPhone()).isEqualTo("+100");
        assertThat(r.getRatingAvg()).isEqualTo("4.85");
        assertThat(r.getVehicle().getPlate()).isEqualTo("ABC-123");
        assertThat(r.getVehicle().getCapacity()).isEqualTo(4);
        assertThat(r.getVehicle().getType()).isEqualTo(com.wayfare.grpc.driver.RideType.COMFORT);
    }

    @Test
    void getDriverWithoutVehicleOrRatingLeavesThemUnset() {
        Driver d = driver(UUID.randomUUID(), DriverStatus.ONBOARDING);
        when(drivers.findById(d.getId())).thenReturn(java.util.Optional.of(d));
        when(vehicles.findByDriverId(d.getId())).thenReturn(java.util.Optional.empty());

        DriverResponse r = stub.getDriver(get(d.getId().toString()));

        assertThat(r.hasVehicle()).isFalse();
        assertThat(r.hasRatingAvg()).isFalse();
        assertThat(r.getStatus()).isEqualTo(com.wayfare.grpc.driver.DriverStatus.ONBOARDING);
    }

    @Test
    void getDriverUnknownIsNotFound() {
        UUID id = UUID.randomUUID();
        when(drivers.findById(id)).thenReturn(java.util.Optional.empty());

        assertThat(codeOf(() -> stub.getDriver(get(id.toString())))).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void getDriverBadUuidIsInvalidArgument() {
        assertThat(codeOf(() -> stub.getDriver(get("nope")))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(codeOf(() -> stub.getDriver(get("")))).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ---- batchGetDrivers ----

    @Test
    void batchJoinsVehiclesAndOmitsUnknownIds() {
        Driver a = driver(UUID.randomUUID(), DriverStatus.APPROVED);
        Driver b = driver(UUID.randomUUID(), DriverStatus.SUSPENDED);
        UUID unknown = UUID.randomUUID();
        when(drivers.findAllById(anyCollection())).thenReturn(List.of(a, b));
        when(vehicles.findByDriverIdIn(anyCollection())).thenReturn(List.of(vehicle(a.getId(), RideType.XL)));

        BatchGetDriversResponse r = stub.batchGetDrivers(batch(a.getId(), b.getId(), unknown));

        assertThat(r.getDriversList()).extracting(DriverResponse::getDriverId)
                .containsExactly(a.getId().toString(), b.getId().toString());
        assertThat(r.getDrivers(0).getVehicle().getType()).isEqualTo(com.wayfare.grpc.driver.RideType.XL);
        assertThat(r.getDrivers(1).hasVehicle()).isFalse();
        assertThat(r.getDrivers(1).getStatus()).isEqualTo(com.wayfare.grpc.driver.DriverStatus.SUSPENDED);
    }

    @Test
    void batchDeduplicatesIds() {
        UUID id = UUID.randomUUID();
        when(drivers.findAllById(anyCollection())).thenReturn(List.of());
        when(vehicles.findByDriverIdIn(anyCollection())).thenReturn(List.of());

        stub.batchGetDrivers(batch(id, id, id));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(drivers).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly(id);
    }

    @Test
    void batchEmptyReturnsEmptyWithoutQueries() {
        BatchGetDriversResponse r = stub.batchGetDrivers(BatchGetDriversRequest.getDefaultInstance());

        assertThat(r.getDriversList()).isEmpty();
        verify(drivers, never()).findAllById(anyCollection());
        verify(vehicles, never()).findByDriverIdIn(anyCollection());
    }

    @Test
    void batchOverTheCapIsInvalidArgument() {
        BatchGetDriversRequest.Builder req = BatchGetDriversRequest.newBuilder();
        for (int i = 0; i <= DriverGrpcService.MAX_BATCH; i++) {
            req.addDriverIds(UUID.randomUUID().toString());
        }

        assertThat(codeOf(() -> stub.batchGetDrivers(req.build()))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(drivers, never()).findAllById(anyCollection());
    }

    @Test
    void batchAtTheCapIsAccepted() {
        BatchGetDriversRequest.Builder req = BatchGetDriversRequest.newBuilder();
        for (int i = 0; i < DriverGrpcService.MAX_BATCH; i++) {
            req.addDriverIds(UUID.randomUUID().toString());
        }
        when(drivers.findAllById(anyCollection())).thenReturn(List.of());
        when(vehicles.findByDriverIdIn(anyCollection())).thenReturn(List.of());

        assertThat(stub.batchGetDrivers(req.build()).getDriversList()).isEmpty();
    }

    @Test
    void batchBadUuidIsInvalidArgumentAndQueriesNothing() {
        BatchGetDriversRequest req = BatchGetDriversRequest.newBuilder()
                .addDriverIds(UUID.randomUUID().toString()).addDriverIds("nope").build();

        assertThat(codeOf(() -> stub.batchGetDrivers(req))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(drivers, never()).findAllById(anyCollection());
    }

    @Test
    void vehicleWithoutTypeIsUnspecified() {
        Driver d = driver(UUID.randomUUID(), DriverStatus.APPROVED);
        Vehicle v = vehicle(d.getId(), null);
        when(drivers.findAllById(anyCollection())).thenReturn(List.of(d));
        when(vehicles.findByDriverIdIn(anyCollection())).thenReturn(List.of(v));

        DriverResponse r = stub.batchGetDrivers(batch(d.getId())).getDrivers(0);

        assertThat(r.getVehicle().getType()).isEqualTo(com.wayfare.grpc.driver.RideType.RIDE_TYPE_UNSPECIFIED);
    }

    // ---- helpers ----

    private static Driver driver(UUID id, DriverStatus status) {
        Driver d = new Driver(id, "d@example.com");
        d.setName("Sam");
        d.setPhone("+100");
        d.setStatus(status);
        return d;
    }

    private static Vehicle vehicle(UUID driverId, RideType type) {
        Vehicle v = new Vehicle();
        v.setDriverId(driverId);
        v.setMake("Toyota");
        v.setModel("Corolla");
        v.setPlate("ABC-123");
        v.setColor("white");
        v.setCapacity(4);
        v.setType(type);
        return v;
    }

    private static GetDriverRequest get(String id) {
        return GetDriverRequest.newBuilder().setDriverId(id).build();
    }

    private static BatchGetDriversRequest batch(UUID... ids) {
        BatchGetDriversRequest.Builder b = BatchGetDriversRequest.newBuilder();
        for (UUID id : ids) {
            b.addDriverIds(id.toString());
        }
        return b.build();
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
