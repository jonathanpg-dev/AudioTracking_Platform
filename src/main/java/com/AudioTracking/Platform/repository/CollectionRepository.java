package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    List<Collection> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
