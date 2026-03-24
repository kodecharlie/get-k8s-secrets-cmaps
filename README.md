# Helm K8s Key/Value ANTLR4 Parser

This grammar and Java walker extract key/value pairs from Kubernetes `ConfigMap` and `Secret` manifests, including Helm-templated values (for example `{{ .Values.foo }}`).
When a `values.yaml` file is provided, simple `.Values.*` references are resolved to their concrete scalar values.

It targets keys under:

- `data:`
- `stringData:`

...when the document `kind` is either `ConfigMap` or `Secret`.

## Files

- `HelmK8sKeyValue.g4` - line-oriented ANTLR4 grammar
- `src/main/java/com/minerva/helmkv/HelmK8sKeyValueExtractor.java` - parse + tree-walk collector
- `src/main/java/com/minerva/helmkv/ParsedKeyValue.java` - result record type

## Maven (Java 17)

This directory is set up as a Maven project with ANTLR4 source generation.

From `tools/antlr4`:

```bash
mvn clean package
```

This runs ANTLR4 generation and compiles all Java sources.

## Run

After building, run the shaded jar (includes runtime dependencies such as `antlr4-runtime` and SnakeYAML):

```bash
java -jar target/helm-k8s-keyvalue-parser-1.0.0-SNAPSHOT-all.jar \
  ../../charts/minervacq/common/templates/secrets.yaml
```

Or resolve values from a `values.yaml` source:

```bash
java -jar target/helm-k8s-keyvalue-parser-1.0.0-SNAPSHOT-all.jar \
  ./example/secrets.yaml \
  ./example/values.yaml
```

Output format:

```text
<kind>.<section>.<key>=<value>
```

For a key like:

```yaml
awsAccessKeyId: "{{ .Values.minerva.secrets.awsAccessKeyId }}"
```

the output value is expanded when the referenced values entry is a scalar string/number/boolean.
