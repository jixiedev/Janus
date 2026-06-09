package com.wish.support;

import com.wish.models.context.Context;
import org.springframework.ai.chat.messages.Message;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generic formatting heuristics for agent console output and step traces. */
public final class AgentRunOutputResolver {

    private static final Pattern STEP_PREFIX = Pattern.compile("^Step \\d+:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBSERVATION = Pattern.compile("\\{observation=(.*),\\s*success=(true|false)}", Pattern.DOTALL);

    private AgentRunOutputResolver() {}

    public static String formatRunTrace(List<String> stepResults) {
        if (stepResults == null || stepResults.isEmpty()) {
            return "No steps executed";
        }
        return String.join("\n", stepResults);
    }

    /**
     * Picks the last step result that looks user-facing, using content heuristics only.
     * Agents with a dedicated result tool should override {@link com.wish.agent.base.BaseAgent#extractUserVisibleOutput}.
     */
    public static String extractUserVisibleFromSteps(Context context, List<String> stepResults) {
        if (stepResults != null) {
            for (int i = stepResults.size() - 1; i >= 0; i--) {
                String body = stripStepPrefix(stepResults.get(i));
                String candidate = unwrapObservation(body);
                if (isReadableStepContent(candidate)) {
                    return candidate;
                }
            }
        }

        if (context != null) {
            String assistant = context.getLastAssistantText();
            if (assistant != null && !assistant.isBlank()) {
                return assistant.trim();
            }
        }
        return "No user-visible result captured.";
    }

    /** @deprecated Prefer {@link DirectToolResultResolver#lookupToolResponse}. */
    @Deprecated
    public static String findLastToolResponse(List<Message> messages, String toolName) {
        return DirectToolResultResolver.lookupToolResponse(messages, toolName);
    }

    public static boolean isReadableStepContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 20) {
            return false;
        }
        if (isShortStatusMessage(trimmed)) {
            return false;
        }
        if (isLikelyJsonPayload(trimmed)) {
            return false;
        }
        if (isInternalAgentHint(trimmed)) {
            return false;
        }
        return true;
    }

    public static boolean isReadableToolResult(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = unwrapObservation(text.trim());
        if (isLikelyJsonPayload(normalized)) {
            return false;
        }
        return normalized.length() <= 2_000;
    }

    public static String unwrapObservation(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = OBSERVATION.matcher(text.trim());
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    private static String stripStepPrefix(String stepLine) {
        if (stepLine == null) {
            return "";
        }
        return STEP_PREFIX.matcher(stepLine.trim()).replaceFirst("").trim();
    }

    private static boolean isShortStatusMessage(String text) {
        return text.startsWith("The interaction has been completed with status:");
    }

    private static boolean isInternalAgentHint(String text) {
        return text.startsWith("Answer must be delivered via ");
    }

    private static boolean isLikelyJsonPayload(String text) {
        if (text.length() < 2) {
            return false;
        }
        char first = text.charAt(0);
        return first == '{' || first == '[';
    }

}
