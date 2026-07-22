package com.yoursay.agents.postagent;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentControllerContractTest {

    @Test
    void restEndpointsDeclareSpecificResponseRecords() throws Exception {
        assertEquals(AgentJobDto.class, AgentController.class
                .getDeclaredMethod("start", GenerateAgentPostRequest.class, String.class)
                .getReturnType());
        assertEquals(AgentJobDto.class, AgentController.class
                .getDeclaredMethod("get", UUID.class, String.class)
                .getReturnType());
    }
}
