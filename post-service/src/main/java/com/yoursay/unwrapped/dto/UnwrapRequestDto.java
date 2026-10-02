package com.yoursay.unwrapped.dto;

/**
 * Result of a reader tapping Unwrap.
 *
 * @param queued true when the post had enough votes and was marked for milestone reconciliation;
 *               false when it is still below the reader-generation threshold
 */
public record UnwrapRequestDto(Long postId, boolean queued) {
}
