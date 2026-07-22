package com.yoursay.posts;

import java.util.Optional;

/** Read-only public face for another domain to validate and aggregate a post's vote options. */
public interface PostVotingConfigurationService {
    Optional<PostVotingConfigurationDto> findByPostId(Long postId);
}
