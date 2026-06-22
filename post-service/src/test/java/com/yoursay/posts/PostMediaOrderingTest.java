package com.yoursay.posts;

import com.yoursay.posts.model.Post;
import com.yoursay.posts.model.PostMedia;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pure logic on the Post aggregate: media gets a stable insertion ordinal. No framework boot.
 */
class PostMediaOrderingTest {

    @Test
    void addMediaAssignsAscendingOrdinalsAndBackReference() {
        Post post = new Post(7L, "Title", "Summary", "Do you agree?", false);

        // Deliberately pass descending/wrong constructor ordinals so the test proves addMedia
        // OVERWRITES them with the insertion index (not that the constructor value happened to fit).
        PostMedia first = new PostMedia(post, MediaType.IMAGE, "posts/a.jpg", "image/jpeg", null, 9);
        PostMedia second = new PostMedia(post, MediaType.VIDEO, "posts/b.mp4", "video/mp4", "posts/b.jpg", 5);
        PostMedia third = new PostMedia(post, MediaType.IMAGE, "posts/c.png", "image/png", null, 2);

        post.addMedia(first);
        post.addMedia(second);
        post.addMedia(third);

        List<PostMedia> media = post.getMedia();
        assertEquals(3, media.size());
        assertEquals(0, media.get(0).getOrdinal());
        assertEquals(1, media.get(1).getOrdinal());
        assertEquals(2, media.get(2).getOrdinal());
        // ordinal reflects insertion order regardless of the value passed to the constructor
        assertSame(first, media.get(0));
        assertSame(second, media.get(1));
        assertSame(third, media.get(2));
        // each item points back at its owning post
        assertSame(post, second.getPost());
    }
}
