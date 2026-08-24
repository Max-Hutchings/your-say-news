package com.yoursay.autopost.agent;

import com.yoursay.platform.ai.AiWebSearchModelConfigurer;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoPostModelCustomizerTest {

    @Test
    void delegatesTheAutoPostNamedModelToSharedWebSearchConfiguration() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer webSearch = Mockito.mock(AiWebSearchModelConfigurer.class);
        AutoPostModelCustomizer customizer = new AutoPostModelCustomizer();
        customizer.webSearch = webSearch;

        customizer.customize(builder);

        Mockito.verify(webSearch).configure(builder);
        assertEquals("autopost", AutoPostModelCustomizer.class.getAnnotation(ModelName.class).value());
    }
}
