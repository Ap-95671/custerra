package com.nci.backend.provider.firms;

import java.util.*;

/** Small bounded RFC-4180 reader; supports quoted commas, newlines and escaped quotes. */
final class FirmsCsv {
    private FirmsCsv() {}
    static List<Map<String, String>> rows(String text) {
        var records = new ArrayList<List<String>>();
        var row = new ArrayList<String>(); var field = new StringBuilder();
        boolean quoted = false, closed = false;
        text = text.replaceFirst("^\uFEFF", "");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
            } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
            else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); closed = false;
                if (c != ',') {
                    if (!(row.size() == 1 && row.getFirst().isBlank())) records.add(List.copyOf(row));
                    row.clear();
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                }
            } else {
                if (closed || c == '"') throw new IllegalArgumentException("Invalid CSV quoting");
                field.append(c);
            }
            if (field.length() > 2000 || records.size() > 20000) throw new IllegalArgumentException("Oversized CSV");
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV field");
        if (!field.isEmpty() || !row.isEmpty() || closed) { row.add(field.toString()); records.add(row); }
        if (records.isEmpty()) throw new IllegalArgumentException("Missing CSV header");
        var header = records.removeFirst();
        if (new HashSet<>(header).size() != header.size() || !header.containsAll(List.of(
                "latitude", "longitude", "acq_date", "acq_time", "satellite", "instrument", "confidence", "bright_ti4", "frp", "daynight")))
            throw new IllegalArgumentException("Unexpected FIRMS schema");
        var rows = new ArrayList<Map<String, String>>();
        for (var record : records) {
            if (record.size() != header.size()) throw new IllegalArgumentException("Invalid CSV width");
            var fields = new HashMap<String, String>();
            for (int i = 0; i < header.size(); i++) fields.put(header.get(i), record.get(i).trim());
            rows.add(fields);
        }
        return rows;
    }
}
