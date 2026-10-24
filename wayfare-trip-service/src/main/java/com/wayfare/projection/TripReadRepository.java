package com.wayfare.projection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TripReadRepository extends MongoRepository<TripReadModel, String> {

    Page<TripReadModel> findByRiderIdOrderByCreatedAtDesc(String riderId, Pageable pageable);

    Page<TripReadModel> findByDriverIdOrderByCreatedAtDesc(String driverId, Pageable pageable);
}
