package com.example.hr.filter;

import com.example.hr.timing.TimingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * JAX-RS filter that measures request processing time and adds it as response headers.
 * Captures total request time and detailed timing breakdown from TimingContext.
 *
 * Headers added:
 * - X-HR-Events-Time-Ms: Total request processing time
 * - X-HR-Events-Timing-Details: JSON with breakdown (db_write, outbox_write, etc.)
 */
@Provider
public class TimingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_TIME_KEY = "request-start-time";
    private static final String TIMING_HEADER = "X-HR-Events-Time-Ms";
    private static final String TIMING_DETAILS_HEADER = "X-HR-Events-Timing-Details";

    @Inject
    TimingContext timingContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestContext.setProperty(START_TIME_KEY, System.currentTimeMillis());
        timingContext.startRequest();
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Long startTime = (Long) requestContext.getProperty(START_TIME_KEY);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            responseContext.getHeaders().add(TIMING_HEADER, String.valueOf(duration));
            responseContext.getHeaders().add(TIMING_DETAILS_HEADER, timingContext.toJson());
        }
    }
}
