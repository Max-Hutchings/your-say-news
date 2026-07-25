package com.yoursay.unwrapped;

/** Small durable boundary invoked after a canonical vote is persisted. */
public interface UnwrappedMilestoneService {
    void markForReconciliation(Long postId);
}
