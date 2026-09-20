package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture CSV minimale (RFC 4180) : séparateur virgule, champs entre guillemets pouvant contenir
 * virgules, guillemets doublés et retours à la ligne. Suffisant pour les exports SDMX de la BCE
 * dont les libellés contiennent des virgules.
 */
final class SimpleCsv {

    private SimpleCsv() {
    }

    /** Lignes non vides du contenu, chacune découpée en champs. */
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                rowHasContent = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                rowHasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                if (rowHasContent || field.length() > 0) {
                    row.add(field.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                rowHasContent = false;
            } else {
                field.append(c);
                rowHasContent = true;
            }
        }
        if (rowHasContent || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
