package com.AudioTracking.Platform.repository;

import com.AudioTracking.Platform.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    // Scoping by id AND owner in one query: a wrong id and someone else's Client id produce the
    // exact same "not found" result — same pattern as every other owned entity in this app.
    Optional<Client> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    // Backs CurrentUserResponse.isLinkedAsClient -- true whenever this User is the login account
    // for at least one Client record, regardless of whether that Client currently has any
    // Projects assigned. See UserServiceImpl#getCurrentUser.
    boolean existsByLinkedUserId(UUID userId);
}
