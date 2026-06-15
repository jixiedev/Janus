package com.wish.models.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global index of all persisted incident reports under {@link IncidentReportPaths#resolveRoot()}.
 * Upserted on every successful {@link IncidentReportStore#persist}.
 */
@Slf4j
public class IncidentReportIndex {

    public static final String INDEX_FILENAME = "incident-reports-index.jsonl";

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Path resolveIndexPath() {
        return IncidentReportPaths.resolveRoot().resolve(INDEX_FILENAME);
    }

    /** Replaces the entry for {@code entry.reportPath()} or appends when new. */
    public synchronized void upsert(IncidentReportIndexEntry entry) {
        if (entry == null || entry.reportPath() == null || entry.reportPath().isBlank()) {
            return;
        }
        Path indexPath = resolveIndexPath();
        try {
            IncidentReportPaths.ensureDirectory(indexPath.getParent());
            Map<String, IncidentReportIndexEntry> byPath = new LinkedHashMap<>();
            if (Files.isRegularFile(indexPath)) {
                for (IncidentReportIndexEntry existing : readAll(indexPath)) {
                    byPath.put(existing.reportPath(), existing);
                }
            }
            byPath.put(entry.reportPath(), entry);
            List<IncidentReportIndexEntry> sorted =
                    byPath.values().stream()
                            .sorted(Comparator.comparing(IncidentReportIndexEntry::reportTime).reversed())
                            .toList();
            writeAll(indexPath, sorted);
            log.debug("Incident report index updated: {}", entry.reportPath());
        } catch (IOException e) {
            log.warn("Failed to update incident report index at {}: {}", indexPath, e.getMessage());
        }
    }

    public List<IncidentReportIndexEntry> readAll() throws IOException {
        Path indexPath = resolveIndexPath();
        if (!Files.isRegularFile(indexPath)) {
            return List.of();
        }
        return readAll(indexPath);
    }

    private static List<IncidentReportIndexEntry> readAll(Path indexPath) throws IOException {
        List<IncidentReportIndexEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(indexPath, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            entries.add(MAPPER.readValue(line, IncidentReportIndexEntry.class));
        }
        return entries;
    }

    private static void writeAll(Path indexPath, List<IncidentReportIndexEntry> entries) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (IncidentReportIndexEntry entry : entries) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(MAPPER.writeValueAsString(entry));
        }
        if (builder.isEmpty()) {
            Files.deleteIfExists(indexPath);
        } else {
            Files.writeString(indexPath, builder.append('\n').toString(), StandardCharsets.UTF_8);
        }
    }
}
