package io.github.fishstiz.lodur.utils;

public class CSVBuilder {
    private final StringBuilder builder = new StringBuilder();
    private boolean separate;

    public CSVBuilder newLine() {
        builder.append("\n");
        this.separate = false;
        return this;
    }

    public CSVBuilder value(Object value) {
        if (this.separate) {
            builder.append(",");
        }
        builder.append(value);
        this.separate = true;
        return this;
    }

    public CSVBuilder values(Object... values) {
        for (Object v : values) {
            value(v);
        }
        return this;
    }

    public String build() {
        return builder.toString();
    }
}
