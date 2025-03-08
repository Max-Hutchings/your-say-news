package com.yoursay.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Post extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @Column(name="title", nullable = false)
    private String title;

    @Column(name="description", nullable=false)
    private String description;

    @Column(name="posted_date", nullable=false)
    private LocalDate postedDate;

    @Column(name="imageUrl")
    private String imageUrl;

    @PrePersist
    protected void onCreate() {
        postedDate = LocalDate.now();
    }


    public Post(Long userId, String title, String description, String imageUrl) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public Post(Long id, Long userId, String title, String description, LocalDate postedDate, String imageUrl) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.postedDate = postedDate;
        this.imageUrl = imageUrl;
    }

    public Post() {

    }

    public Long getId(){
        if (id != null) return id;
        return null;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
