package com.flowdesk.dashboard;

/** Minimal RFC 4180 CSV builder that also neutralises spreadsheet formula injection. */
public final class CsvWriter {

    private final StringBuilder out = new StringBuilder();

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String s = value.toString();
        // A cell starting with = + - @ (or tab/CR) can be executed as a formula by Excel/Sheets.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
