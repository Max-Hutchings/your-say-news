package com.yoursay.votes;

/** Aggregate count/share for one stable option inside one characteristic bucket. */
public record ChoiceSentiment(Long optionId, long count, double percentage) {
}
