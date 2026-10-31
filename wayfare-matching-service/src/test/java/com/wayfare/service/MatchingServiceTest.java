package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wayfare.domain.ApprovedDriver;
import com.wayfare.domain.NearbyDriver;
import com.wayfare.event.MatchEventPublisher;

import io.grpc.Status;

class MatchingServiceTest {

    static final String TRIP = "trip-1";
    static final double LAT = 30.0, LNG = 31.0;

    CandidateFinder finder = mock(CandidateFinder.class);
    DriverDirectory directory = mock(DriverDirectory.class);
    DriverReservation reservation = mock(DriverReservation.class);
    DispatchGuard guard = mock(DispatchGuard.class);
    MatchEventPublisher publisher = mock(MatchEventPublisher.class);
    MatchingService service = new MatchingService(TestProperties.defaults(), finder, directory,
            new CandidateScorer(TestProperties.defaults()), reservation, guard, publisher);

    @BeforeEach
    void setUp() {
        when(guard.tryBegin(TRIP)).thenReturn(true);
        when(finder.find(anyString(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        when(reservation.tryReserve(anyString(), eq(TRIP))).thenReturn(true);
    }

    @Test
    void firstRadiusMatchSkipsLargerRadii() {
        hits(2.0, near("a", 1.0));
        approve("a");

        service.dispatch(TRIP, LAT, LNG);

        verify(publisher).driverMatched(TRIP, "a");
        verify(finder, never()).find(anyString(), anyDouble(), anyDouble(), eq(4.0), anyInt());
    }

    @Test
    void emptyFirstRadiusFallsThroughToTheNext() {
        hits(4.0, near("b", 3.0));
        approve("b");

        service.dispatch(TRIP, LAT, LNG);

        verify(publisher).driverMatched(TRIP, "b");
    }

    @Test
    void bestScoredDriverIsReservedFirst() {
        hits(2.0, near("far", 1.8), near("near", 0.3));
        approve("far", "near");

        service.dispatch(TRIP, LAT, LNG);

        verify(publisher).driverMatched(TRIP, "near");
        verify(reservation, never()).tryReserve("far", TRIP);
    }

    @Test
    void takenDriverFallsBackToTheNextBest() {
        hits(2.0, near("best", 0.3), near("second", 1.0));
        approve("best", "second");
        when(reservation.tryReserve("best", TRIP)).thenReturn(false);

        service.dispatch(TRIP, LAT, LNG);

        verify(publisher).driverMatched(TRIP, "second");
    }

    @Test
    void unapprovedDriversAreNeverReserved() {
        hits(2.0, near("suspended", 0.3), near("ok", 1.5));
        approve("ok");

        service.dispatch(TRIP, LAT, LNG);

        verify(reservation, never()).tryReserve("suspended", TRIP);
        verify(publisher).driverMatched(TRIP, "ok");
    }

    @Test
    void driverSeenInASmallerRadiusIsNotAskedAgain() {
        hits(2.0, near("x", 1.0));
        hits(4.0, near("x", 1.0), near("y", 3.0));
        hits(8.0, near("x", 1.0), near("y", 3.0));
        when(directory.approved(anyCollection())).thenReturn(Map.of());

        service.dispatch(TRIP, LAT, LNG);

        verify(directory).approved(List.of("x"));
        verify(directory).approved(List.of("y"));
        verify(publisher).noDriversAvailable(TRIP);
    }

    @Test
    void nobodyAvailablePublishesNoDriversAndKeepsTheGuard() {
        service.dispatch(TRIP, LAT, LNG);

        verify(publisher).noDriversAvailable(TRIP);
        verify(guard, never()).release(TRIP);
        verifyNoInteractions(directory);
    }

    @Test
    void duplicateDeliveryDoesNothing() {
        when(guard.tryBegin(TRIP)).thenReturn(false);

        service.dispatch(TRIP, LAT, LNG);

        verifyNoInteractions(finder, directory, reservation, publisher);
    }

    @Test
    void directoryFailureReleasesTheGuardAndPropagates() {
        hits(2.0, near("a", 1.0));
        when(directory.approved(anyCollection())).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> service.dispatch(TRIP, LAT, LNG)).hasMessageContaining("UNAVAILABLE");

        verify(guard).release(TRIP);
        verifyNoInteractions(publisher);
    }

    @Test
    void publishFailureReleasesTheDriverAndTheGuard() {
        hits(2.0, near("a", 1.0));
        approve("a");
        org.mockito.Mockito.doThrow(new IllegalStateException("kafka down"))
                .when(publisher).driverMatched(TRIP, "a");

        assertThatThrownBy(() -> service.dispatch(TRIP, LAT, LNG)).hasMessage("kafka down");

        verify(reservation).release("a", TRIP);
        verify(guard).release(TRIP);
    }

    // ---- helpers ----

    private void hits(double radiusKm, NearbyDriver... drivers) {
        when(finder.find(eq("default"), eq(LAT), eq(LNG), eq(radiusKm), eq(20))).thenReturn(List.of(drivers));
    }

    private void approve(String... ids) {
        Map<String, ApprovedDriver> approved = Arrays.stream(ids)
                .collect(Collectors.toMap(id -> id, id -> new ApprovedDriver(id, 4.5)));
        when(directory.approved(anyCollection())).thenReturn(approved);
    }

    private static NearbyDriver near(String id, double km) {
        return new NearbyDriver(id, km);
    }
}
