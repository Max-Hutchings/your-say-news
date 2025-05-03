package com.yoursay.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class YourSayUserRepository implements PanacheRepository<YourSayUser> {

    public Uni<YourSayUser> findYourSayUserById(Long id) {
        return findById(id);
    }

    public Uni<YourSayUser> findByEmail(String email) {
        return YourSayUser.find("email", email)
            .firstResult();
    }

    public Uni<YourSayUser> saveYourSayUser(YourSayUser yourSayUser) {
        return yourSayUser
                .persist()
                .chain(() -> findByEmail(yourSayUser.getEmail()));
    }

    public Uni<YourSayUser> deleteYourSayUser(Long id) {
        return findById(id)
                .onItem().ifNotNull().invoke(user -> user.setActive(false)) // Mark user as inactive (soft delete)
                .flatMap(user -> user.persist());
    }
}