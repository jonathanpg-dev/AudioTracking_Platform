package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Collection;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    // Sort-parameterized rather than a fixed OrderByCreatedAtDesc suffix, so callers can order by
    // date added or date modified, ascending or descending -- see SortParams and CollectionController.
    List<Collection> findAllByUserId(UUID userId, Sort sort);

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
