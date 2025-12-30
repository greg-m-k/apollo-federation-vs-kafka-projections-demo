// Rhai script to propagate timing headers from subgraphs to the final response

fn supergraph_service(service) {
    // Add collected timing headers to the final response
    let add_timing_to_response = |response| {
        // Total time per subgraph
        let hr_time = response.context["hr_time_ms"];
        if hr_time != () {
            response.headers["X-HR-Time-Ms"] = hr_time;
        }

        let emp_time = response.context["employment_time_ms"];
        if emp_time != () {
            response.headers["X-Employment-Time-Ms"] = emp_time;
        }

        let sec_time = response.context["security_time_ms"];
        if sec_time != () {
            response.headers["X-Security-Time-Ms"] = sec_time;
        }

        // Detailed timing breakdown per subgraph
        let hr_details = response.context["hr_timing_details"];
        if hr_details != () {
            response.headers["X-HR-Timing-Details"] = hr_details;
        }

        let emp_details = response.context["employment_timing_details"];
        if emp_details != () {
            response.headers["X-Employment-Timing-Details"] = emp_details;
        }

        let sec_details = response.context["security_timing_details"];
        if sec_details != () {
            response.headers["X-Security-Timing-Details"] = sec_details;
        }
    };

    service.map_response(add_timing_to_response);
}

fn subgraph_service(service, subgraph) {
    // Capture timing headers from subgraph responses
    let capture_timing = |response| {
        if subgraph == "hr" {
            if "x-hr-time-ms" in response.headers {
                response.context.hr_time_ms = response.headers["x-hr-time-ms"];
            }
            if "x-timing-details" in response.headers {
                response.context.hr_timing_details = response.headers["x-timing-details"];
            }
        }

        if subgraph == "employment" {
            if "x-employment-time-ms" in response.headers {
                response.context.employment_time_ms = response.headers["x-employment-time-ms"];
            }
            if "x-timing-details" in response.headers {
                response.context.employment_timing_details = response.headers["x-timing-details"];
            }
        }

        if subgraph == "security" {
            if "x-security-time-ms" in response.headers {
                response.context.security_time_ms = response.headers["x-security-time-ms"];
            }
            if "x-timing-details" in response.headers {
                response.context.security_timing_details = response.headers["x-timing-details"];
            }
        }
    };

    service.map_response(capture_timing);
}
