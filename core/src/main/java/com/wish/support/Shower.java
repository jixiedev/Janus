package com.wish.support;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.util.function.Consumer;

public class Shower {
    static {
        AnsiConsole.systemInstall();
    }

    private static final Object OUTPUT_LOCK = new Object();
    private static volatile Consumer<String> outputConsumer;

    public static void setOutputConsumer(Consumer<String> consumer) {
        outputConsumer = consumer;
    }

    private static void emitLine(String line) {
        Consumer<String> consumer = outputConsumer;
        if (consumer != null) {
            consumer.accept(line);
            return;
        }
        System.out.println(line);
        System.out.flush();
    }

    public static final Operation LLM_THINK = Operation.LLM_THINK;
    public static final Operation LLM_TOOL = Operation.LLM_TOOL;
    public static final Operation PLAN_PROGRESS = Operation.PLAN_PROGRESS;
    public static final Operation USER = Operation.USER;

    public enum Operation {
        LLM_THINK(Ansi.ansi().fgBrightBlack(), "thought"),
        LLM_TOOL(Ansi.ansi().fgBrightCyan(), "tool"),
        PLAN_PROGRESS(Ansi.ansi().fgYellow(), "plan"),
        USER(Ansi.ansi().fgGreen(), "");

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
                String line = "[" + prefix + "]" + raw.trim();
                if (!agent.isEmpty()) {
                    line = "[" + agent + "]" + line;
                }
                emitLine("");
                emitLine(ansi.a(line).reset().toString());
            }
        }

        private void printUserAnswer(String body, String agent) {
            if (!agent.isEmpty()) {
                emitLine(ansi.a("[" + agent + "]").reset().toString());
            }
            emitLine(ansi.a(body).reset().toString());
            emitLine("");
        }
    }
}
