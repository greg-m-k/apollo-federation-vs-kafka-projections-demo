package com.example.employment.filter;

import com.example.employment.timing.TimingContext;
import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;

/**
 * Vert.x route filter that measures request processing time and adds it as response headers.
 * Captures total request time and detailed timing breakdown from TimingContext.
 */
public class TimingFilter {

    private static final String START_TIME_KEY = "request-start-time";
    private static final String TIMING_HEADER = "X-Subgraph-Time-Ms";
    private static final String TIMING_DETAILS_HEADER = "X-Timing-Details";

    @Inject
    TimingContext timingContext;

    @RouteFilter(100)
    void startTimer(RoutingContext rc) {
        long startTime = System.currentTimeMillis();
        rc.put(START_TIME_KEY, startTime);
        timingContext.startRequest();

        rc.addHeadersEndHandler(v -> {
            Long start = rc.get(START_TIME_KEY);
            if (start != null) {
                long duration = System.currentTimeMillis() - start;
                rc.response().putHeader(TIMING_HEADER, String.valueOf(duration));
                rc.response().putHeader(TIMING_DETAILS_HEADER, timingContext.toJson());
            }
        });
        rc.next();
    }
}
