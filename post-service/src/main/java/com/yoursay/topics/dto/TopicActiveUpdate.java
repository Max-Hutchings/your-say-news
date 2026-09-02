package com.yoursay.topics.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Retire ({@code false}) or restore ({@code true}) a topic. Topics are never deleted — posts already
 * assigned a retired topic keep it, and its feed still works; it simply stops being offered as a new
 * selection.
 */
public record TopicActiveUpdate(@NotNull Boolean active) {
}
