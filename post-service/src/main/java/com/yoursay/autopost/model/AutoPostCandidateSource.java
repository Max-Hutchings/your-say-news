package com.yoursay.autopost.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "auto_post_candidate_source")
public class AutoPostCandidateSource extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "publisher", nullable = false, length = 256)
    private String publisher;

    protected AutoPostCandidateSource() {
    }

    public AutoPostCandidateSource(UUID candidateId, int ordinal, String url, String title,
                                   String publisher) {
        this.id = UUID.randomUUID();
        this.candidateId = candidateId;
        this.ordinal = ordinal;
        this.url = url.trim();
        this.title = title.trim();
        this.publisher = publisher.trim();
    }

    public UUID getCandidateId() { return candidateId; }
    public int getOrdinal() { return ordinal; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getPublisher() { return publisher; }
}
