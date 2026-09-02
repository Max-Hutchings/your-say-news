package com.yoursay.unwrapped.agent;

/** Provider-neutral research/writing boundary implemented with LangChain4j inside this domain. */
public interface UnwrappedResearchGenerator {
    /** Generates a publication candidate and enforces the publication safety contract. */
    UnwrappedResearchResult generate(UnwrappedResearchRequest request);

    /** Generates an unchecked benchmark candidate for direct comparison in the admin UI. */
    UnwrappedResearchResult generate(UnwrappedResearchRequest request, String systemPrompt);
}
