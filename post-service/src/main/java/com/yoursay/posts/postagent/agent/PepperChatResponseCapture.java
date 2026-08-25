package com.yoursay.posts.postagent.agent;

import com.yoursay.platform.ai.AiAgentType;
import com.yoursay.platform.ai.AiTokenMetrics;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Retains Pepper provider metadata when structured-output mapping fails. */
@ApplicationScoped
class PepperChatResponseCapture implements ChatModelListener {
    private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<ChatResponse> response = new ThreadLocal<>();

    @Inject
    AiTokenMetrics tokenMetrics;

    void begin() {
        response.remove();
        active.set(true);
    }

    ChatResponse take() {
        ChatResponse value = response.get();
        clear();
        return value;
    }

    void clear() {
        active.remove();
        response.remove();
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        if (active.get()) {
            response.set(context.chatResponse());
            if (tokenMetrics != null) {
                tokenMetrics.record(AiAgentType.POST_AGENT, context.chatResponse());
            }
        }
    }
}
