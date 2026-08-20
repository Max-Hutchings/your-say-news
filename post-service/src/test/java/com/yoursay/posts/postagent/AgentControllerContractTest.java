package com.yoursay.posts.postagent;

import com.yoursay.posts.postagent.dto.GenerateAgentPostRequest;
import com.yoursay.posts.postagent.dto.AgentGenerationEventDto;
import com.yoursay.posts.postagent.dto.PepperDraftDto;
import com.yoursay.posts.postagent.dto.UpdatePepperDraftRequest;

import io.smallrye.mutiny.Multi;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentControllerContractTest {

    @Test
    void restEndpointsDeclareSpecificResponseRecords() throws Exception {
        var start = AgentController.class
                .getDeclaredMethod("start", GenerateAgentPostRequest.class, String.class);
        assertEquals(Multi.class, start.getReturnType());
        assertTrue(start.getGenericReturnType().getTypeName()
                .contains(AgentGenerationEventDto.class.getName()));
        var events = AgentController.class
                .getDeclaredMethod("events", UUID.class, String.class, String.class);
        assertEquals(Multi.class, events.getReturnType());
        assertTrue(events.getGenericReturnType().getTypeName()
                .contains(AgentGenerationEventDto.class.getName()));
        var latest = AgentController.class.getDeclaredMethod("latest", String.class);
        assertEquals(RestResponse.class, latest.getReturnType());
        assertTrue(latest.getGenericReturnType().getTypeName().contains(PepperDraftDto.class.getName()));
        assertEquals(PepperDraftDto.class, AgentController.class
                .getDeclaredMethod("save", UUID.class, UpdatePepperDraftRequest.class, String.class)
                .getReturnType());
    }
}
