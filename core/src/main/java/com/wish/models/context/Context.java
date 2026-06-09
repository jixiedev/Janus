package com.wish.models.context;

import com.wish.support.ConversationLlmUsage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
     * Compressed user/agent pairs from earlier prompt runs in the same session ({@code session:{id}}).
     * Hydrated at {@link com.wish.models.session.Session#beginPrompt} for read-only background in
     * {@link #initializePromptPrefix}; ordering is applied in {@link PromptPrefixLayout}.
     */
    @Getter
    private List<Message> priorTurnSummaries = List.of();

    /**
     * Start index in chat memory for the current prompt run (set by {@link #initializePromptPrefix}).
     * Skipped by {@link com.wish.models.session.Session#endPrompt} summarization via {@link #getPromptMessages()}.
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
     * Attaches summarized prior turns for this prompt run (typically from {@link com.wish.models.session.Session}).
     */
    public void setPriorTurnSummaries(List<Message> summaries) {
        priorTurnSummaries =
                summaries == null || summaries.isEmpty() ? List.of() : List.copyOf(summaries);
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
                        systemPrompt, priorTurnSummaries, primaryUserRequest, trailingUserBlocks);
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

    public void addUserMemory(String memory) {
        chatMemory.add(conversation, new UserMessage(memory));
    }

    public void addSystemMemory(String memory) {
        chatMemory.add(conversation, new SystemMessage(memory));
    }

    public void replaceMemory(List<Message> messages) {
        chatMemory.clear(conversation);
        if (messages != null && !messages.isEmpty()) {
            chatMemory.add(conversation, messages);
        }
    }

    public void addMemory(Message message) {
        chatMemory.add(conversation, message);
    }

    public List<Message> getAllMessages() {
        return chatMemory.get(conversation);
    }

    /**
     * Messages produced during this prompt run (excludes the assembled prefix).
     * Used as input to session summarization; excludes ephemeral nextStepPrompt when
     * {@link com.wish.llm.LLMChatClient#askWithTools} is called with {@code persistNewMessages=false}.
     */
    public List<Message> getPromptMessages() {
        List<Message> messages = getAllMessages();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int from = getPromptRunStartIndex();
        if (from >= messages.size()) {
            return List.of();
        }
        return List.copyOf(messages.subList(from, messages.size()));
    }

    public String getLastAssistantText() {
        List<Message> messages = chatMemory.get(conversation);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistant) {
                String text = assistant.getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
