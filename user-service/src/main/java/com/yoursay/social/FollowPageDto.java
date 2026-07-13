package com.yoursay.social;

import java.util.List;

/**
 * One page of a followers / following list. {@code hasMore} tells the client whether a further
 * page exists, so it can drive infinite scroll without a separate count call.
 */
public record FollowPageDto(
        List<FollowUserDto> items,
        boolean hasMore
) {
}
