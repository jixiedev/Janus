package com.wish.models.context;

import com.wish.support.ConversationLlmUsage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

@Getter
public class Context {

    /** @see ConversationLlmUsage#DEFAULT_TAG */
    public static final String DEFAULT_USAGE_TAG = ConversationLlmUsage.DEFAULT_TAG;

    protected final String conversation;

    /** When set, tools (e.g. bash) scope to this id across prompt runs in the same session. */
    protected final String sessionId;

    protected final ChatMemory chatMemory;

    protected final ConversationLlmUsage llmUsage = new ConversationLlmUsage();

    /**
     * Summarized prior turns bound at {@link #beginPrompt} for the next prefix assembly.
     * Optimization: session content is supplied here; ordering is applied in {@link #initializePromptPrefix}.
     */
    private List<Message> boundSessionMessages = List.of();

    /**
     * Start index in chat memory for the current prompt run (set by {@link #initializePromptPrefix}).
     * Skipped by {@link com.wish.models.session.Session#endPrompt} summarization via
     * {@link BaseUserContext#getPromptMessages()}.
     */
    @Getter
    @Setter
    private int promptRunStartIndex;

    /** Original shell/API request for this prompt run. */
    @Getter
    @Setter
    private String promptRequest;

    /** {@code agent.run} return value (step outputs); optional hint for summarization. */
    @Getter
    @Setter
    private String runResult;

    public Context(String conversation, ChatMemory chatMemory) {
        this(conversation, null, chatMemory);
    }

    public Context(String conversation, String sessionId, ChatMemory chatMemory) {
        this.conversation = conversation;
        this.sessionId = sessionId;
        this.chatMemory = chatMemory;
    }

    /**
     * Binds prior session summaries for this prompt run (typically from {@link com.wish.models.session.Session}).
     */
    public void bindSessionMessages(List<Message> sessionMessages) {
        boundSessionMessages =
                sessionMessages == null || sessionMessages.isEmpty()
                        ? List.of()
                        : List.copyOf(sessionMessages);
    }

    protected List<Message> getBoundSessionMessages() {
        return boundSessionMessages;
    }

    /**
     * Loads the prompt prefix into {@link #chatMemory} using {@link PromptPrefixLayout}.
     * Optimization: single entry point for system / current task / session ordering.
     *
     * @param trailingUserBlocks variable user text after the primary task, before session history
     */
    public void initializePromptPrefix(
            String systemPrompt, String primaryUserRequest, List<String> trailingUserBlocks) {
        List<Message> prefix =
                PromptPrefixLayout.build(
                        systemPrompt, boundSessionMessages, primaryUserRequest, trailingUserBlocks);
        chatMemory.clear(conversation);
        if (!prefix.isEmpty()) {
            chatMemory.add(conversation, prefix);
        }
        promptRunStartIndex = prefix.size();
    }

    public void initializePromptPrefix(String systemPrompt, String primaryUserRequest) {
        initializePromptPrefix(systemPrompt, primaryUserRequest, List.of());
    }

    public void onResponse(ChatResponse chatResponse) {
        onResponse(chatResponse, DEFAULT_USAGE_TAG);
    }

    public void onResponse(ChatResponse chatResponse, String usageTag) {
        llmUsage.record(chatResponse, usageTag);
    }

    /** Immutable view of token and prompt-cache usage accumulated on this context partition. */
    public ConversationLlmUsage snapshotLlmUsage() {
        return llmUsage.snapshot();
    }
}
