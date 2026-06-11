package com.yoursay.posts;

import java.time.LocalDate;

/**
 * Public post representation for HTTP and cross-domain use.
 */
public record PostDto(
        Long id,
        Long userId,
        String title,
        String description,
        LocalDate postedDate,
        String imageUrl
) {
}
