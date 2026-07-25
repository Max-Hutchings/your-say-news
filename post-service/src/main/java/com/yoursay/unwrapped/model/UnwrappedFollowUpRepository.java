package com.yoursay.unwrapped.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UnwrappedFollowUpRepository implements PanacheRepositoryBase<UnwrappedFollowUp, UUID> {
    public Optional<UnwrappedFollowUp> findByUserAndPost(Long userId, Long postId) {
        return find("userId = ?1 and postId = ?2", userId, postId).firstResultOptional();
    }
}
