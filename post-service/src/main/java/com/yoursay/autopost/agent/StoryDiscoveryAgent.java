package com.yoursay.autopost.agent;

import java.time.Instant;

/** Internal research boundary owned by the auto-post domain. */
public interface StoryDiscoveryAgent {

    StoryDiscoveryResult discover(Instant windowStart, Instant windowEnd);
}
