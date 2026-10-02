package com.yoursay.unwrapped.dto;

/**
 * The Post Unwrapped feature flags the app needs to choose a reader's journey after voting.
 *
 * @param enabled false hides Unwrapped completely; the backend then refuses every Unwrapped call
 * @param unwrapButton true sends a voter to the results with an Unwrap button rather than straight
 *                     into Unwrapped
 */
public record UnwrappedFeaturesDto(boolean enabled, boolean unwrapButton) {
}
