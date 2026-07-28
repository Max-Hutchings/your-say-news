package com.yoursay.unwrapped.agent;

/** Provider-neutral research/writing boundary implemented with LangChain4j inside this domain. */
public interface UnwrappedResearchGenerator {
    UnwrappedResearchResult generate(UnwrappedResearchRequest request);
}
