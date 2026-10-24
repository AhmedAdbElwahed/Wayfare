package com.wayfare.repository;

import com.wayfare.domain.TripEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripEventRepository extends JpaRepository<TripEventRecord, UUID> {
}
