# New Relic OpenTelemetry exporter
An [OpenTelemetry](https://github.com/open-telemetry/opentelemetry-java) reporter for sending spans and metrics
to New Relic using the New Relic Java Telemetry SDK.

For the details on how OpenTelemetry data is mapped to New Relic data, see documentation in
[Our exporter specifications documentation](https://github.com/newrelic/newrelic-exporter-specs)

### How to use

To send spans or metrics to New Relic, you will need an [Insights Insert API Key](https://docs.newrelic.com/docs/insights/insights-data-sources/custom-data/introduction-event-api#).

Note: There is an example [BasicExample.java](opentelemetry-exporters-newrelic/src/test/java/com/newrelic/telemetry/opentelemetry/examples/BasicExample.java)  
in the test source code hierarchy that matches this example code. It should be considered as the canonical code for this example, since OpenTelemetry internal SDK APIs are still a work in progress.

#### Quickstart
If you want to get started quickly, the easiest way to configure the OpenTelemetry SDK and the
New Relic exporters is like this:

```java
    Configuration configuration = new Configuration(apiKey, "My Service Name")
        .enableAuditLogging()
        .collectionIntervalSeconds(10);
    NewRelicExporters.start(configuration);
```

Be sure to shut down the exporters when your application finishes:

```
   NewRelicExporters.shutdown();
```

#### If you need more flexibility, you can set up the individual exporters and the SDK by hand:

##### For spans:

Important: If you are using [auto-instrumentation](#auto-instrumentation), or you have used the
[quickstart](#quickstart) you should skip the configuration of the SDK, and go right to the next
section.

1. Create a `NewRelicSpanExporter`
```java
    NewRelicSpanExporter exporter = NewRelicSpanExporter.newBuilder()
        .apiKey(System.getenv("INSIGHTS_INSERT_KEY"))
        .commonAttributes(new Attributes().put(SERVICE_NAME, "best service ever")).build();
```

2. Build the OpenTelemetry `BatchSpansProcessor` with the `NewRelicSpanExporter` 
```java
    BatchSpanProcessor spanProcessor = BatchSpanProcessor.newBuilder(exporter).build();
```

3. Add the span processor to the default TracerSdkProvider
```java
    TracerSdkProvider tracerSdkProvider = (TracerSdkProvider) OpenTelemetry.getTracerProvider();
    tracerSdkProvider.addSpanProcessor(spanProcessor);
```

##### Use the APIs to record some spans

1. Create the OpenTelemetry `Tracer` and use it for recording spans.
```java
    Tracer tracer = OpenTelemetry.getTracerProvider().get("sample-app", "1.0");
    
    Span span = tracer.spanBuilder("testSpan").setSpanKind(Kind.INTERNAL).startSpan();
    try (Scope scope = tracer.withSpan(span)) {
      //do some work
      Thread.sleep(1000);
      span.end();
    }
```

2. Find your spans in New Relic One: go to https://one.newrelic.com/ and select **Distributed Tracing**.

##### For metrics:

Important: If you are using [auto-instrumentation](#auto-instrumentation), or you have used the
[quickstart](#quickstart) you should skip the configuration of the SDK, and go right to the next
section.

1. Create a `NewRelicMetricExporter`

```java
    MetricExporter metricExporter =
        NewRelicMetricExporter.newBuilder()
          .apiKey(System.getenv("INSIGHTS_INSERT_KEY"))
          .commonAttributes(new Attributes().put(SERVICE_NAME, "best service ever"))
          .build();
```

2. Create an `IntervalMetricReader` that will batch up metrics every 5 seconds:

```java
    IntervalMetricReader intervalMetricReader =
        IntervalMetricReader.builder()
            .setMetricProducers(
                Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
            .setExportIntervalMillis(5000)
            .setMetricExporter(metricExporter)
            .build();
```

##### Use the APIs to record some metrics

1. Create a sample Meter:

```java
    Meter meter = OpenTelemetry.getMeterProvider().get("sample-app", "1.0");
```

2. Here is an example of a counter:

```java
    LongCounter spanCounter =
        meter
            .longCounterBuilder("spanCounter")
            .setUnit("one")
            .setDescription("Counting all the spans")
            .setMonotonic(true)
            .build();
```

3. Here is an example of a measure:

```java
    LongMeasure spanTimer =
        meter
            .longMeasureBuilder("spanTimer")
            .setUnit("ms")
            .setDescription("How long the spans take")
            .setAbsolute(true)
            .build();
```

4. Use these instruments for recording some metrics:

```java
   spanCounter.add(1, "spanName", "testSpan", "isItAnError", "true");
   spanTimer.record(1000, "spanName", "testSpan")
```

5. Find your metrics in New Relic One: go to https://one.newrelic.com/ and locate your service
in the **Entity explorer** (based on the `"service.name"` attributes you've used).

### Auto-Instrumentation

To instrument tracers and meters using [opentelemetry-auto-instr-java](https://github.com/open-telemetry/opentelemetry-auto-instr-java),
`opentelemetry-exporter-newrelic-auto-<version>.jar` can be used to provide opentelemetry exporters. Here is an example.

```bash
java -javaagent:path/to/opentelemetry-auto-<version>.jar \
     -Dota.exporter.jar=path/to/opentelemetry-exporter-newrelic-auto-<version>.jar \
     -Dota.exporter.newrelic.api.key=${INSIGHTS_INSERT_KEY} \
     -Dota.exporter.newrelic.service.name=best-service-ever \
     -jar myapp.jar
```

If you wish to turn on debug logging for the exporter running in the auto-instrumentation agent, use the following system property:
```
-Dio.opentelemetry.auto.slf4j.simpleLogger.log.com.newrelic.telemetry=debug
```

And, if you wish to enable audit logging for the exporter running in the auto-instrumentaiotn agent, use this system property:
```
-Dota.exporter.newrelic.enable.audit.logging=true
```

### Javadoc for this project can be found here: [![Javadocs][javadoc-image]][javadoc-url]

### Find and use your data

For tips on how to find and query your data in New Relic, see [Find trace/span data](https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/introduction-trace-api#view-data). 

For general querying information, see:
- [Query New Relic data](https://docs.newrelic.com/docs/using-new-relic/data/understand-data/query-new-relic-data)
- [Intro to NRQL](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/introduction-nrql)


### Gradle
`build.gradle`:

```
repositories {
    maven {
        url = "https://oss.sonatype.org/content/repositories/snapshots"
    }
}
```

```
implementation("com.newrelic.telemetry:opentelemetry-exporters-newrelic:0.6.0")
implementation("io.opentelemetry:opentelemetry-sdk:0.6.0")
implementation("com.newrelic.telemetry:telemetry-core:0.6.1")
implementation("com.newrelic.telemetry:telemetry-http-okhttp:0.6.1")
```

### Building
CI builds are run on Azure Pipelines:
[![Build status](https://dev.azure.com/NRAzurePipelines/Java%20CI/_apis/build/status/PR%20Build%20for%20OpenTelemetry%20Exporters?branchName=
main)](https://dev.azure.com/NRAzurePipelines/Java%20CI/_build/latest?definitionId=11&branchName=main)

The project uses gradle 5 for building, and the gradle wrapper is provided.

To compile, run the tests and build the jar:

`$ ./gradlew build`

### Contributing
Full details are available in our [CONTRIBUTING.md file](CONTRIBUTING.md). We'd love to get your contributions to improve the New Relic OpenTelemetry exporter! Keep in mind when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project. To execute our corporate CLA, which is required if your contribution is on behalf of a company, or if you have any questions, please drop us an email at open-source@newrelic.com.

[javadoc-image]: https://www.javadoc.io/badge/com.newrelic.telemetry/opentelemetry-exporters-newrelic.svg
[javadoc-url]: https://www.javadoc.io/doc/com.newrelic.telemetry/opentelemetry-exporters-newrelic
