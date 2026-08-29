package com.wayfare.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wayfare.domain.Profile;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

}
