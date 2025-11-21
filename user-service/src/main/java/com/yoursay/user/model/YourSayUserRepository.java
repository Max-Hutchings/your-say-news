package com.yoursay.user.model;


import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class YourSayUserRepository implements PanacheRepository<YourSayUser> {

    public YourSayUser findYourSayUserById(Long id) {
        return findById(id);
    }

    public YourSayUser findByEmail(String email) {
        return YourSayUser.find("email", email)
            .firstResult();
    }

    public YourSayUser saveYourSayUser(YourSayUser yourSayUser) {
        yourSayUser.persist();
        Panache.getEntityManager().refresh(yourSayUser);
        return yourSayUser;
    }


}