/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.telemetry.opentelemetry.examples;

import static com.newrelic.telemetry.opentelemetry.export.AttributeNames.SERVICE_NAME;

import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.MetricBatchSenderFactory;
import com.newrelic.telemetry.OkHttpPoster;
import com.newrelic.telemetry.SpanBatchSenderFactory;
import com.newrelic.telemetry.TelemetryClient;
import com.newrelic.telemetry.metrics.MetricBatchSender;
import com.newrelic.telemetry.opentelemetry.export.NewRelicMetricExporter;
import com.newrelic.telemetry.opentelemetry.export.NewRelicSpanExporter;
import com.newrelic.telemetry.spans.SpanBatchSender;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.common.Labels;
import io.opentelemetry.context.Scope;
import io.opentelemetry.metrics.LongCounter;
import io.opentelemetry.metrics.LongValueRecorder;
import io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.Random;

public class BasicExample {

  public static void main(String[] args) throws InterruptedException {
    // NOTE: Under "normal" circumstances, you wouldn't use OpenTelemetry this way. This is just an
    // example to show how you can manually create a tracer for experimentation.
    // See the OpenTelemetry documentation at https://opentelemetry.io for more normal usage.

    String apiKey = System.getenv("INSIGHTS_INSERT_KEY");

    TelemetryClient telemetryClient = createTelemetryClient(apiKey);
    Attributes serviceAttributes = new Attributes().put(SERVICE_NAME, "best service ever");

    // 1. Create a `NewRelicSpanExporter`
    SpanExporter exporter =
        NewRelicSpanExporter.newBuilder()
            .telemetryClient(telemetryClient)
            .commonAttributes(serviceAttributes)
            .build();

    // 2. Create a `NewRelicMetricExporter`
    MetricExporter metricExporter =
        NewRelicMetricExporter.newBuilder()
            .telemetryClient(telemetryClient)
            .commonAttributes(serviceAttributes)
            .build();

    // 3. Build the OpenTelemetry `BatchSpanProcessor` with the `NewRelicSpanExporter`
    BatchSpanProcessor spanProcessor = BatchSpanProcessor.newBuilder(exporter).build();

    // 4. Add the span processor to the TracerProvider from the SDK
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(spanProcessor);

    // 5. Create an `IntervalMetricReader` that will batch up metrics every 5 seconds.
    IntervalMetricReader intervalMetricReader =
        IntervalMetricReader.builder()
            .setMetricProducers(
                Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
            .setExportIntervalMillis(5000)
            .setMetricExporter(metricExporter)
            .build();

    // Now, we've got the SDK fully configured. Let's write some very simple instrumentation to
    // demonstrate how it all works.

    // 6. Create an OpenTelemetry `Tracer` and a `Meter` and use them for some manual
    // instrumentation.
    Tracer tracer = OpenTelemetry.getTracerProvider().get("sample-app", "1.0");
    Meter meter = OpenTelemetry.getMeterProvider().get("sample-app", "1.0");

    // 7. Here is an example of a counter
    LongCounter spanCounter =
        meter
            .longCounterBuilder("spanCounter")
            .setUnit("one")
            .setDescription("Counting all the spans")
            .build();

    // 8. Here's an example of a measure.
    LongValueRecorder spanTimer =
        meter
            .longValueRecorderBuilder("spanTimer")
            .setUnit("ms")
            .setDescription("How long the spans take")
            .build();

    // 9. Optionally, you can pre-bind a set of labels, rather than passing them in every time.
    BoundLongValueRecorder boundTimer = spanTimer.bind(Labels.of("spanName", "testSpan"));

    // 10. use these to instrument some work
    doSomeSimulatedWork(tracer, spanCounter, boundTimer);

    // clean up so the JVM can exit. Note: these will flush any data to the exporter
    // before they exit.
    intervalMetricReader.shutdown();
    spanProcessor.shutdown();
  }

  private static TelemetryClient createTelemetryClient(String apiKey) {
    MetricBatchSender metricBatchSender =
        MetricBatchSender.create(
            MetricBatchSenderFactory.fromHttpImplementation(OkHttpPoster::new)
                .configureWith(apiKey)
                .auditLoggingEnabled(true)
                .build());
    SpanBatchSender spanBatchSender =
        SpanBatchSender.create(
            SpanBatchSenderFactory.fromHttpImplementation(OkHttpPoster::new)
                .configureWith(apiKey)
                .auditLoggingEnabled(true)
                .build());

    return new TelemetryClient(metricBatchSender, spanBatchSender, null, null);
    // note: if you just want the simple defaults, you can use this method:
    // return TelemetryClient.create(OkHttpPoster::new, apiKey);
  }

  private static void doSomeSimulatedWork(
      Tracer tracer, LongCounter spanCounter, BoundLongValueRecorder boundTimer)
      throws InterruptedException {
    Random random = new Random();
    for (int i = 0; i < 10; i++) {
      long startTime = System.currentTimeMillis();
      Span span =
          tracer.spanBuilder("testSpan").setSpanKind(Kind.INTERNAL).setNoParent().startSpan();
      try (Scope ignored = tracer.withSpan(span)) {
        boolean markAsError = random.nextBoolean();
        if (markAsError) {
          span.setStatus(Status.INTERNAL.withDescription("internalError"));
        }
        spanCounter.add(1, Labels.of("spanName", "testSpan", "isItAnError", "" + markAsError));
        // do some work
        Thread.sleep(random.nextInt(1000));
        span.end();
        boundTimer.record(System.currentTimeMillis() - startTime);
      }
    }
  }
}
