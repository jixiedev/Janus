package com.wish.model.stage;

import java.util.List;

/** How {@link Stage} selects MCP tools from the shell-registered catalog. */
public enum McpToolPolicy {
    /** No MCP tools for this stage. */
    NONE,
    /** All registered MCP tools. */
    ALL;

    public List<Object> select(List<Object> registeredMcpTools) {
        if (this == NONE || registeredMcpTools == null || registeredMcpTools.isEmpty()) {
            return List.of();
        }
        return List.copyOf(registeredMcpTools);
    }
}
