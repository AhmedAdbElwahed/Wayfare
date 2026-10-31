package com.wayfare.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.wayfare.config.MatchingProperties;
import com.wayfare.domain.ApprovedDriver;
import com.wayfare.grpc.driver.BatchGetDriversRequest;
import com.wayfare.grpc.driver.DriverResponse;
import com.wayfare.grpc.driver.DriverServiceGrpc;
import com.wayfare.grpc.driver.DriverStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asks the Driver Service which candidates may drive. Redis knows where drivers
 * are, not whether they are approved, suspended or deleted.
 *
 * <p>A failed call ({@code StatusRuntimeException}) propagates: the dispatch
 * fails and Kafka redelivers, rather than matching unchecked drivers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverDirectory {

    private final DriverServiceGrpc.DriverServiceBlockingStub stub;
    private final MatchingProperties properties;

    /** The APPROVED drivers among {@code driverIds}, keyed by id. Unknown ids are absent. */
    public Map<String, ApprovedDriver> approved(Collection<String> driverIds) {
        List<String> ids = driverIds.stream().filter(DriverDirectory::isUuid).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > MatchingProperties.MAX_DRIVER_BATCH) {
            throw new IllegalArgumentException("At most " + MatchingProperties.MAX_DRIVER_BATCH + " ids per lookup");
        }

        List<DriverResponse> drivers = stub
                .withDeadlineAfter(properties.driverLookupTimeout().toMillis(), MILLISECONDS)
                .batchGetDrivers(BatchGetDriversRequest.newBuilder().addAllDriverIds(ids).build())
                .getDriversList();

        Map<String, ApprovedDriver> approved = new LinkedHashMap<>();
        for (DriverResponse d : drivers) {
            if (d.getStatus() == DriverStatus.APPROVED) {
                approved.put(d.getDriverId(), new ApprovedDriver(d.getDriverId(), rating(d)));
            }
        }
        return approved;
    }

    private static Double rating(DriverResponse d) {
        if (!d.hasRatingAvg()) {
            return null;
        }
        try {
            return Double.valueOf(d.getRatingAvg());
        } catch (NumberFormatException e) {
            log.warn("Driver {} has unparseable rating '{}'", d.getDriverId(), d.getRatingAvg());
            return null;
        }
    }

    /** One bad geo-set member would make the whole batch INVALID_ARGUMENT; skip it instead. */
    private static boolean isUuid(String id) {
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring non-UUID driver id '{}' in the geo set", id);
            return false;
        }
    }
}
