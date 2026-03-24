package com.minerva.helmkv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.yaml.snakeyaml.Yaml;

public final class HelmK8sKeyValueExtractor {
    private HelmK8sKeyValueExtractor() {
    }

    public static List<ParsedKeyValue> parse(String content) {
        return parse(content, Collections.emptyMap());
    }

    public static List<ParsedKeyValue> parse(String content, String valuesYamlContent) {
        return parse(content, parseValuesYaml(valuesYamlContent));
    }

    public static List<ParsedKeyValue> parse(String content, Map<String, Object> valuesRoot) {
        HelmK8sKeyValueLexer lexer = new HelmK8sKeyValueLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HelmK8sKeyValueParser parser = new HelmK8sKeyValueParser(tokens);

        CollectorListener listener = new CollectorListener(new ValuesResolver(valuesRoot));
        ParseTreeWalker.DEFAULT.walk(listener, parser.file());
        return listener.results();
    }

    public static List<ParsedKeyValue> parse(Path yamlFile) throws IOException {
        return parse(Files.readString(yamlFile));
    }

    public static List<ParsedKeyValue> parse(Path yamlFile, Path valuesFile) throws IOException {
        return parse(Files.readString(yamlFile), Files.readString(valuesFile));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 2) {
            System.err.println("Usage: HelmK8sKeyValueExtractor <yaml-template-file> [values-yaml-file]");
            System.exit(1);
        }

        List<ParsedKeyValue> entries = args.length == 2
            ? parse(Path.of(args[0]), Path.of(args[1]))
            : parse(Path.of(args[0]));
        for (ParsedKeyValue e : entries) {
            System.out.printf("%s.%s.%s=%s%n", e.getKind(), e.getSection(), e.getKey(), e.getValue());
        }
    }

    private static final class CollectorListener extends HelmK8sKeyValueBaseListener {
        private static final String KIND_CONFIG_MAP = "ConfigMap";
        private static final String KIND_SECRET = "Secret";

        private final ValuesResolver valuesResolver;
        private String currentKind = null;
        private String currentSection = null;
        private final List<ParsedKeyValue> results = new ArrayList<>();

        CollectorListener(ValuesResolver valuesResolver) {
            this.valuesResolver = valuesResolver;
        }

        @Override
        public void enterElement(HelmK8sKeyValueParser.ElementContext ctx) {
            if (ctx.DOC_SEP() != null) {
                currentKind = null;
                currentSection = null;
                return;
            }

            if (ctx.KIND_LINE() != null) {
                String line = ctx.KIND_LINE().getText();
                if (line.contains(KIND_CONFIG_MAP)) {
                    currentKind = KIND_CONFIG_MAP;
                } else if (line.contains(KIND_SECRET)) {
                    currentKind = KIND_SECRET;
                } else {
                    currentKind = null;
                }
                currentSection = null;
                return;
            }

            if (ctx.SECTION_LINE() != null) {
                String line = ctx.SECTION_LINE().getText();
                if (line.contains("stringData")) {
                    currentSection = "stringData";
                } else if (line.contains("data")) {
                    currentSection = "data";
                } else {
                    currentSection = null;
                }
                return;
            }

            if (ctx.KV_LINE() != null && isTargetDocument()) {
                String rawLine = trimLineEnd(ctx.KV_LINE().getText());
                int colonIndex = rawLine.indexOf(':');
                if (colonIndex <= 0) {
                    return;
                }

                String key = rawLine.substring(0, colonIndex).trim();
                String value = rawLine.substring(colonIndex + 1).trim();
                String resolvedValue = valuesResolver.resolveValueReference(value);
                results.add(new ParsedKeyValue(currentKind, currentSection, unquote(key), resolvedValue));
            }
        }

        private boolean isTargetDocument() {
            if (currentKind == null || currentSection == null) {
                return false;
            }
            if (!(KIND_CONFIG_MAP.equals(currentKind) || KIND_SECRET.equals(currentKind))) {
                return false;
            }
            return "data".equals(currentSection) || "stringData".equals(currentSection);
        }

        private static String trimLineEnd(String s) {
            return s.replaceAll("[\\r\\n]+$", "");
        }

        private static String unquote(String s) {
            if (s.length() >= 2) {
                if ((s.startsWith("\"") && s.endsWith("\""))
                    || (s.startsWith("'") && s.endsWith("'"))) {
                    return s.substring(1, s.length() - 1);
                }
            }
            return s;
        }

        List<ParsedKeyValue> results() {
            return results;
        }
    }

    private static Map<String, Object> parseValuesYaml(String valuesYamlContent) {
        if (valuesYamlContent == null || valuesYamlContent.isBlank()) {
            return Collections.emptyMap();
        }
        Object parsed = new Yaml().load(valuesYamlContent);
        if (parsed instanceof Map<?, ?> parsedMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) parsedMap;
            return casted;
        }
        return Collections.emptyMap();
    }

    private static final class ValuesResolver {
        private static final Pattern SIMPLE_VALUES_REFERENCE = Pattern.compile(
            "^['\"]?\\{\\{\\s*\\.Values\\.([A-Za-z0-9_.\\-]+)\\s*\\}\\}['\"]?$"
        );

        private final Map<String, Object> valuesRoot;

        ValuesResolver(Map<String, Object> valuesRoot) {
            this.valuesRoot = valuesRoot == null ? Collections.emptyMap() : valuesRoot;
        }

        String resolveValueReference(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return rawValue;
            }
            Matcher matcher = SIMPLE_VALUES_REFERENCE.matcher(rawValue.trim());
            if (!matcher.matches()) {
                return rawValue;
            }

            Object resolved = lookupByDotPath(matcher.group(1));
            if (resolved == null || resolved instanceof Map<?, ?> || resolved instanceof List<?>) {
                return rawValue;
            }
            return String.valueOf(resolved);
        }

        private Object lookupByDotPath(String dotPath) {
            String[] segments = dotPath.split("\\.");
            Object current = valuesRoot;
            for (String segment : segments) {
                if (!(current instanceof Map<?, ?> currentMap)) {
                    return null;
                }
                if (!currentMap.containsKey(segment)) {
                    return null;
                }
                current = currentMap.get(segment);
            }
            return current;
        }
    }
}
