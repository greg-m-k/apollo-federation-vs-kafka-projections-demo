package com.example.hr.graphql;

import com.example.hr.timing.TimingContext;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Produces a GraphQL instrumentation that adds timing data to response extensions.
 * This allows Apollo Router to forward timing via include_subgraph_extensions.
 */
@ApplicationScoped
public class TimingInstrumentation {

    @Inject
    Instance<TimingContext> timingContextInstance;

    @Produces
    @ApplicationScoped
    public Instrumentation createTimingInstrumentation() {
        return new SimplePerformantInstrumentation() {
            @Override
            public CompletableFuture<ExecutionResult> instrumentExecutionResult(
                    ExecutionResult executionResult,
                    InstrumentationExecutionParameters parameters,
                    InstrumentationState state) {

                TimingContext timingContext = timingContextInstance.get();

                // Build timing extension
                Map<String, Object> timing = new HashMap<>();
                timing.putAll(timingContext.getTimings());
                timing.put("total", timingContext.getTotalTime());
                timing.put("subgraph", "hr");

                // Merge with existing extensions
                Map<String, Object> extensions = executionResult.getExtensions() != null
                        ? new HashMap<>(executionResult.getExtensions())
                        : new HashMap<>();
                extensions.put("timing", timing);

                // Return new result with timing in extensions
                ExecutionResult result = ExecutionResultImpl.newExecutionResult()
                        .data(executionResult.getData())
                        .errors(executionResult.getErrors())
                        .extensions(extensions)
                        .build();

                return CompletableFuture.completedFuture(result);
            }
        };
    }
}
