package com.yoursay.unwrapped.agent;

import com.yoursay.platform.ai.AiWebSearchModelConfigurer;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnwrappedModelCustomizerTest {
    @Test
    void delegatesTheUnwrappedNamedModelToSharedWebSearchConfiguration() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer webSearch = Mockito.mock(AiWebSearchModelConfigurer.class);
        UnwrappedModelCustomizer customizer = new UnwrappedModelCustomizer();
        customizer.webSearch = webSearch;

        customizer.customize(builder);

        Mockito.verify(webSearch).configure(builder);
        assertEquals("unwrapped", UnwrappedModelCustomizer.class.getAnnotation(ModelName.class).value());
    }
}
