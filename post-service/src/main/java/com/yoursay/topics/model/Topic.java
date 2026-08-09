package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry in the controlled topic catalogue (ADR-043). The catalogue ships as reference data in
 * migration {@code 0015} and is extended at runtime by admins.
 *
 * <p>The id is the canonical string ({@code housing}, {@code cost-of-living}) and is the primary
 * key: {@code post_topic} rows, feed query parameters and any future classifier output all name a
 * topic by it, so it never changes. A topic is retired by clearing {@link #active} rather than
 * deleted, which keeps historical assignments intelligible.
 */
@Entity
@Table(name = "topic")
public class Topic extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "display_group", nullable = false, length = 64)
    private String displayGroup;

    /** Position in the mobile tab strip; admin-created topics take {@code max + 1}. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Topic() {
    }

    public Topic(String id, String label, String displayGroup, int displayOrder) {
        this.id = id;
        this.label = label;
        this.displayGroup = displayGroup;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayGroup() {
        return displayGroup;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Retire or restore a topic. Retired topics stay assigned to the posts that already carry them. */
    public void setActive(boolean active) {
        this.active = active;
    }
}
