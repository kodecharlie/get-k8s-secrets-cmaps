package com.minerva.helmkv;

public final class ParsedKeyValue {
    private final String kind;
    private final String section;
    private final String key;
    private final String value;

    public ParsedKeyValue(String kind, String section, String key, String value) {
        this.kind = kind;
        this.section = section;
        this.key = key;
        this.value = value;
    }

    public String getKind() {
        return kind;
    }

    public String getSection() {
        return section;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ParsedKeyValue{"
            + "kind='" + kind + '\''
            + ", section='" + section + '\''
            + ", key='" + key + '\''
            + ", value='" + value + '\''
            + '}';
    }
}
