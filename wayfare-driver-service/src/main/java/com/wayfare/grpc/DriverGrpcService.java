package com.wayfare.grpc;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.grpc.server.service.GrpcService;

import com.wayfare.domain.Driver;
import com.wayfare.domain.Vehicle;
import com.wayfare.grpc.driver.BatchGetDriversRequest;
import com.wayfare.grpc.driver.BatchGetDriversResponse;
import com.wayfare.grpc.driver.DriverResponse;
import com.wayfare.grpc.driver.DriverServiceGrpc;
import com.wayfare.grpc.driver.DriverStatus;
import com.wayfare.grpc.driver.GetDriverRequest;
import com.wayfare.repository.DriverRepository;
import com.wayfare.repository.VehicleRepository;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;

@GrpcService
@RequiredArgsConstructor
public class DriverGrpcService extends DriverServiceGrpc.DriverServiceImplBase {

    static final String DRIVER_NOT_FOUND = "DRIVER_NOT_FOUND";
    static final int MAX_BATCH = 100;

    private final DriverRepository drivers;
    private final VehicleRepository vehicles;

    @Override
    public void getDriver(GetDriverRequest request, StreamObserver<DriverResponse> out) {
        UUID driverId = parseUUID(request.getDriverId(), out);
        if (driverId == null) {
            return;
        }
        var driver = drivers.findById(driverId);
        if (driver.isEmpty()) {
            out.onError(Status.NOT_FOUND.withDescription(DRIVER_NOT_FOUND).asRuntimeException());
            return;
        }
        // A driver without a vehicle is still a valid answer: the vehicle stays unset.
        var vehicle = vehicles.findByDriverId(driverId).orElse(null);
        out.onNext(toResponse(driver.get(), vehicle));
        out.onCompleted();
    }

    @Override
    public void batchGetDrivers(BatchGetDriversRequest request, StreamObserver<BatchGetDriversResponse> out) {
        if (request.getDriverIdsCount() > MAX_BATCH) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("at most " + MAX_BATCH + " driver_ids per call").asRuntimeException());
            return;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String raw : request.getDriverIdsList()) {
            UUID id = parseUUID(raw, out);
            if (id == null) {
                return; // parseUUID already failed the call
            }
            ids.add(id);
        }

        BatchGetDriversResponse.Builder response = BatchGetDriversResponse.newBuilder();
        if (!ids.isEmpty()) {
            Map<UUID, Vehicle> vehicleByDriver = vehicles.findByDriverIdIn(ids).stream()
                    .collect(Collectors.toMap(Vehicle::getDriverId, Function.identity()));
            for (Driver driver : drivers.findAllById(ids)) {
                response.addDrivers(toResponse(driver, vehicleByDriver.get(driver.getId())));
            }
        }
        out.onNext(response.build());
        out.onCompleted();
    }

    private static DriverResponse toResponse(Driver driver, Vehicle vehicle) {
        DriverResponse.Builder builder = DriverResponse.newBuilder().setDriverId(driver.getId().toString())
                .setName(nullToEmpty(driver.getName())).setPhone(nullToEmpty(driver.getPhone()))
                .setStatus(DriverStatus.valueOf(driver.getStatus().name()));

        if (driver.getRatingAvg() != null) {
            builder.setRatingAvg(driver.getRatingAvg().toPlainString());
        }

        if (vehicle != null) {
            builder.setVehicle(toVehicle(vehicle));
        }
        return builder.build();
    }

    private static com.wayfare.grpc.driver.Vehicle toVehicle(Vehicle vehicle) {

        return com.wayfare.grpc.driver.Vehicle.newBuilder().setMake(nullToEmpty(vehicle.getMake()))
                .setModel(nullToEmpty(vehicle.getModel())).setPlate(nullToEmpty(vehicle.getPlate()))
                .setColor(nullToEmpty(vehicle.getColor())).setCapacity(vehicle.getCapacity())
                .setType(vehicle.getType() == null ? com.wayfare.grpc.driver.RideType.RIDE_TYPE_UNSPECIFIED
                        : com.wayfare.grpc.driver.RideType.valueOf(vehicle.getType().name()))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static UUID parseUUID(String id, StreamObserver<?> out) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("invalid UUID: " + id).asRuntimeException());
            return null;
        }
    }

}
