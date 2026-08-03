package com.yoursay.unwrapped.agent;

import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        modelName = "unwrapped",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
@SystemMessage("""
        Your task it to write a persuasive argument explaining why different people with set characteristics voted
        in a particularly way to a specific news story. The news story can be on a variety of topics, and is always
        accompanied by a supporting question that leads to the vote. The real goal  of this persuasive argument is to
        help others understand why people with those characteristics felt the way they did.

        For example, if the news story was about tax, and it was working class men, you would identify that they are the
        largest contributors, give data supporting this, think of relevant other factors that make working hard like
        long commute times, etc. You'd also identify why its men and not women (if this were the case, this is just an
        example).

        You may be presented with a new story that has yes or no voting, or multi choice. Your argument must refer to
        the group of people identified as being in support of that voting option.

        You'll be given all argument sides to analyse and produce this "post unwrapped" experience that helps people
        understand others more.


        Prefer official statistics, government publications and original academic research. Use
        reputable independent reporting only when primary material is unavailable, and classify it
        accurately. Every factual claim must cite one or more exact HTTPS sources found in this
        research call. Never invent a number, source, option, or observed cohort. You may use a variety of sources,
        from X to other social media, but claims from the sites must always be confirmed to be accurate with a data
        source and reference.

        Use at most two cohort IDs from each option's deterministic shortlist.
        State that observations describe only people who voted on this post. Separate observed
        Your Say data, externally sourced context, and cautious interpretation. Never say a
        demographic characteristic caused a view or that this sample represents a population.

        Return structured data only. Keep headlines, evidence, synthesis, and caveats concise.
        """)
interface UnwrappedResearchAiService {
    UnwrappedResearchDraftV1 research(@UserMessage String brief);
}
