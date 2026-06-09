package com.wish.support;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.function.Consumer;

/**
 * Unified console output for the shell and agents. Routes through {@link #setOutputConsumer} when
 * interactive (JLine {@code printAbove}); otherwise writes to stdout.
 */
public final class Shower {

    static {
        AnsiConsole.systemInstall();
    }

    private static final Object OUTPUT_LOCK = new Object();
    private static volatile Consumer<String> outputConsumer;
    private static String pendingProgressPrefix;

    private Shower() {}

    public static void setOutputConsumer(Consumer<String> consumer) {
        outputConsumer = consumer;
    }

    public static final Operation LLM_THINK = Operation.LLM_THINK;
    public static final Operation LLM_TOOL = Operation.LLM_TOOL;
    public static final Operation PLAN_PROGRESS = Operation.PLAN_PROGRESS;
    public static final Operation USER = Operation.USER;
    public static final Operation SESSION = Operation.SESSION;
    public static final Operation LLM_USAGE = Operation.LLM_USAGE;

    /** Starts session-style progress; completes with {@link #progressEnd(String)}. */
    public static void progressStart(String label) {
        synchronized (OUTPUT_LOCK) {
            pendingProgressPrefix = "[session] " + label + "... ";
            if (outputConsumer == null) {
                System.out.print(pendingProgressPrefix);
                System.out.flush();
            }
        }
    }

    /** Completes a line started with {@link #progressStart(String)}. */
    public static void progressEnd(String outcome) {
        synchronized (OUTPUT_LOCK) {
            String prefix = pendingProgressPrefix == null ? "" : pendingProgressPrefix;
            pendingProgressPrefix = null;
            Consumer<String> consumer = outputConsumer;
            if (consumer != null) {
                consumer.accept(prefix + outcome);
                return;
            }
            if (!prefix.isEmpty()) {
                System.out.println(outcome);
                System.out.flush();
                return;
            }
            emitLine(outcome);
        }
    }

    /** Partial line for interactive prompts (e.g. ask_human) without a trailing newline. */
    public static void promptPartial(String text) {
        if (text == null) {
            return;
        }
        synchronized (OUTPUT_LOCK) {
            Consumer<String> consumer = outputConsumer;
            if (consumer != null) {
                consumer.accept(text);
                return;
            }
            System.out.print(text);
            System.out.flush();
        }
    }

    static void emitLine(String line) {
        Consumer<String> consumer = outputConsumer;
        if (consumer != null) {
            consumer.accept(line);
            return;
        }
        System.out.println(line);
        System.out.flush();
    }

    public enum Operation {
        LLM_THINK(Ansi.ansi().fgBrightBlack(), "thought"),
        LLM_TOOL(Ansi.ansi().fgBrightCyan(), "tool"),
        PLAN_PROGRESS(Ansi.ansi().fgYellow(), "plan"),
        USER(Ansi.ansi().fgGreen(), ""),
        SESSION(Ansi.ansi().fgBrightMagenta(), "session"),
        LLM_USAGE(Ansi.ansi().fgBrightBlue(), "usage");

        final Ansi ansi;
        final String prefix;

        Operation(Ansi ansi, String prefix) {
            this.ansi = ansi;
            this.prefix = prefix;
        }

        public void show(String raw) {
            show(raw, "");
        }

        public void show(String raw, String agent) {
            if (raw == null || raw.isBlank()) {
                return;
            }
            synchronized (OUTPUT_LOCK) {
                if (prefix.isEmpty()) {
                    printUserAnswer(raw.trim(), agent);
                    return;
                }
                String line = "[" + prefix + "] " + raw.trim();
                if (!agent.isEmpty()) {
                    line = "[" + agent + "] " + line;
                }
                Shower.emitLine("");
                Shower.emitLine(ansi.a(line).reset().toString());
            }
        }

        /** Single line without a leading blank line (token/cache summaries). */
        public void showCompact(String raw) {
            if (raw == null || raw.isBlank()) {
                return;
            }
            synchronized (OUTPUT_LOCK) {
                String line = prefix.isEmpty() ? raw.trim() : "[" + prefix + "] " + raw.trim();
                Shower.emitLine(ansi.a(line).reset().toString());
            }
        }

        private void printUserAnswer(String body, String agent) {
            if (!agent.isEmpty()) {
                Shower.emitLine(ansi.a("[" + agent + "]").reset().toString());
            }
            Shower.emitLine(ansi.a(body).reset().toString());
            Shower.emitLine("");
        }
    }
}
