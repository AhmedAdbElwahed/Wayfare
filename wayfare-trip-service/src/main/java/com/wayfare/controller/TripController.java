package com.wayfare.controller;

import com.wayfare.dto.CancelRequest;
import com.wayfare.dto.CompleteTripRequest;
import com.wayfare.dto.CreateTripRequest;
import com.wayfare.dto.TripDto;
import com.wayfare.exception.TripAccessDeniedException;
import com.wayfare.exception.TripNotFoundException;
import com.wayfare.projection.TripReadModel;
import com.wayfare.projection.TripReadRepository;
import com.wayfare.service.TripCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Writes go to {@link TripCommandService} (PostgreSQL) and answer 202 Accepted —
 * the read model catches up asynchronously. Reads go straight to MongoDB.
 */
@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripCommandService commands;
    private final TripReadRepository readRepo;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid CreateTripRequest req) {
        return commands.createTrip(caller(jwt), req.pickup(), req.dropoff());
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        commands.confirmTrip(id, caller(jwt));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                       @RequestBody(required = false) @Valid CancelRequest req) {
        commands.cancelTrip(id, caller(jwt), req == null ? null : req.reason());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/arriving")
    public ResponseEntity<Void> arriving(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        commands.driverArriving(id, caller(jwt));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        commands.startTrip(id, caller(jwt));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                         @RequestBody(required = false) @Valid CompleteTripRequest req) {
        commands.completeTrip(id, caller(jwt), req == null ? null : req.distanceKm());
        return ResponseEntity.accepted().build();
    }

    // --- queries (MongoDB) ---------------------------------------------------

    @GetMapping("/{id}")
    public TripReadModel get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        TripReadModel doc = readRepo.findById(id).orElseThrow(() -> new TripNotFoundException(id));
        String caller = jwt.getSubject();
        if (!caller.equals(doc.getRiderId()) && !caller.equals(doc.getDriverId())) {
            throw new TripAccessDeniedException(id);
        }
        return doc;
    }

    /** The caller's own trips, newest first; driver tokens list trips they drove. */
    @GetMapping
    public Page<TripReadModel> mine(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        PageRequest paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return "DRIVER".equals(jwt.getClaimAsString("role"))
                ? readRepo.findByDriverIdOrderByCreatedAtDesc(jwt.getSubject(), paging)
                : readRepo.findByRiderIdOrderByCreatedAtDesc(jwt.getSubject(), paging);
    }

    private static UUID caller(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
