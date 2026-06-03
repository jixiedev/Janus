package com.wish.models.context;

import com.wish.model.DiagnosticContext;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Per-prompt-run handle for {@link com.wish.models.session.DiagnosticSession}.
 * Router/stage LLM histories stay inside {@link DiagnosticContext}; this context is used for
 * session lifecycle, token accounting, and summarization metadata.
 */
@Getter
public class DiagnosticRunContext extends BaseUserContext {

    @Setter
    private DiagnosticContext diagnosticContext;

    public DiagnosticRunContext(String conversationId, String sessionId, ChatMemory chatMemory) {
        super(conversationId, sessionId, chatMemory);
    }
}
