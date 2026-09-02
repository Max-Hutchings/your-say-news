package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_source")
public class PostSource extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "url", nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "publisher", nullable = false, length = 256)
    private String publisher;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    protected PostSource() {
    }

    public PostSource(Post post, String url, String title, String publisher, int ordinal) {
        this.post = post;
        this.url = url;
        this.title = title;
        this.publisher = publisher;
        this.ordinal = ordinal;
    }

    public void setPost(Post post) { this.post = post; }
    public Post getPost() { return post; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getPublisher() { return publisher; }
    public int getOrdinal() { return ordinal; }
}
