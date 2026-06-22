package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PostMediaUploadRepository implements PanacheRepository<PostMediaUpload> {

    public Uni<PostMediaUpload> saveUpload(PostMediaUpload upload) {
        return persist(upload).replaceWith(upload);
    }

    public Uni<PostMediaUpload> findByKeyAndUser(String s3Key, Long userId) {
        return find("s3Key = :s3Key and userId = :userId",
                Parameters.with("s3Key", s3Key).and("userId", userId)).firstResult();
    }
}
