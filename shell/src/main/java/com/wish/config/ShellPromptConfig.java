package com.wish.config;

import com.wish.support.Shower;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.jline.PromptProvider;

@Configuration
public class ShellPromptConfig {

    private final ObjectProvider<LineReader> lineReaderProvider;

    public ShellPromptConfig(ObjectProvider<LineReader> lineReaderProvider) {
        this.lineReaderProvider = lineReaderProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureShowerOutput() {
        LineReader lineReader = lineReaderProvider.getIfAvailable();
        if (lineReader != null) {
            Shower.setOutputConsumer(lineReader::printAbove);
        }
    }

    @Bean
    public PromptProvider promptProvider(@Value("${janus.shell.prompt:janus:> }") String prompt) {
        return () -> new AttributedString(prompt);
    }
}
