package com.yoursay.posts;

import com.yoursay.posts.dto.PostVotingConfigurationDto;

import java.util.Optional;

/** Read-only public face for another domain to validate and aggregate a post's vote options. */
public interface PostVotingConfigurationService {
    Optional<PostVotingConfigurationDto> findByPostId(Long postId);
}
