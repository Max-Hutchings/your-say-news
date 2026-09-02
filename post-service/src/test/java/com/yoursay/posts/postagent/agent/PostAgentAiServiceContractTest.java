package com.yoursay.posts.postagent.agent;

import com.yoursay.posts.postagent.dto.AgentDraftDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostAgentAiServiceContractTest {

    @Test
    void returnsTheStructuredDraftDirectlyWithoutAResultEnvelope() {
        Method research = java.util.Arrays.stream(PostAgentAiService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("research"))
                .findFirst()
                .orElseThrow();

        assertEquals(AgentDraftDto.class, research.getReturnType());
    }
}
