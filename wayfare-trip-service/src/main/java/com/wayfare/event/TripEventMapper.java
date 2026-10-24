package com.wayfare.event;

import com.wayfare.domain.GeoPoint;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Translates the domain's {@link TripDomainEvent} records into the wire contract
 * ({@code TripEvent}, defined in wayfare-common-events). Keeping this in one
 * place means the domain never depends on generated protobuf classes, and a
 * schema change touches only this mapper.
 */
@Component
public class TripEventMapper {

    public TripEvent toProto(UUID eventId, TripDomainEvent event) {
        TripEvent.Builder b = TripEvent.newBuilder()
                .setEventId(eventId.toString())
                .setTripId(event.tripId().toString())
                .setOccurredAt(ProtoTimes.toTimestamp(event.occurredAt()));

        // Java 17 has no pattern-matching switch, hence the instanceof chain.
        if (event instanceof TripDomainEvent.RideRequested e) {
            b.setRideRequested(RideRequested.newBuilder()
                    .setRiderId(e.riderId().toString())
                    .setPickup(latLng(e.pickup()))
                    .setDropoff(latLng(e.dropoff()))
                    .setFareLocked(e.fareLocked().toPlainString()));
        } else if (event instanceof TripDomainEvent.DriverMatchedApplied e) {
            b.setDriverMatchedApplied(DriverMatchedApplied.newBuilder().setDriverId(e.driverId().toString()));
        } else if (event instanceof TripDomainEvent.NoDriversFound) {
            b.setNoDriversFound(NoDriversFound.getDefaultInstance());
        } else if (event instanceof TripDomainEvent.DriverArriving) {
            b.setDriverArriving(DriverArriving.getDefaultInstance());
        } else if (event instanceof TripDomainEvent.TripStarted) {
            b.setTripStarted(TripStarted.getDefaultInstance());
        } else if (event instanceof TripDomainEvent.TripCompleted e) {
            b.setTripCompleted(TripCompleted.newBuilder()
                    .setRiderId(e.riderId().toString())
                    .setDriverId(e.driverId().toString())
                    .setFinalFare(e.finalFare().toPlainString())
                    .setDistanceKm(e.distanceKm()));
        } else if (event instanceof TripDomainEvent.TripCancelled e) {
            TripCancelled.Builder c = TripCancelled.newBuilder().setCancelledBy(e.cancelledBy());
            if (e.reason() != null) {
                c.setReason(e.reason());
            }
            b.setTripCancelled(c);
        }
        return b.build();
    }

    private static LatLng latLng(GeoPoint p) {
        return LatLng.newBuilder().setLat(p.lat()).setLng(p.lng()).build();
    }
}
