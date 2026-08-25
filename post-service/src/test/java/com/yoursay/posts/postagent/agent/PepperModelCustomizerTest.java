package com.yoursay.posts.postagent.agent;

import com.yoursay.platform.ai.AiWebSearchModelConfigurer;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PepperModelCustomizerTest {

    @Test
    void delegatesThePepperNamedModelToSharedWebSearchConfiguration() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer webSearch = Mockito.mock(AiWebSearchModelConfigurer.class);
        PepperModelCustomizer customizer = new PepperModelCustomizer();
        customizer.webSearch = webSearch;

        customizer.customize(builder);

        Mockito.verify(webSearch).configure(builder);
        Mockito.verify(builder).textVerbosity("low");
        assertEquals("pepper", PepperModelCustomizer.class.getAnnotation(ModelName.class).value());
    }
}
