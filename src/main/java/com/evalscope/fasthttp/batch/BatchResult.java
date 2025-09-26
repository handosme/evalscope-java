package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import java.util.*;

public class BatchResult {
    private final String batchId;
    private final List<Result> results;
    private final long totalTimeMs;
    private final int successfulRequests;
    private final int failedRequests;
    private final boolean completed;
    private final boolean cancelled;
    private final String terminationReason;

    private BatchResult(String batchId, List<Result> results,
                       long totalTimeMs, boolean completed, boolean cancelled,
                       String terminationReason) {
        this.batchId = batchId;
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.totalTimeMs = totalTimeMs;
        this.successfulRequests = (int) results.stream()
                .filter(r -> r.success())
                .count();
        this.failedRequests = results.size() - successfulRequests;
        this.completed = completed;
        this.cancelled = cancelled;
        this.terminationReason = terminationReason;
    }

    public String batchId() {
        return batchId;
    }

    public List<Result> results() {
        return results;
    }

    public Optional<Result> result(String requestId) {
        return results.stream()
                .filter(r -> r.requestId().equals(requestId))
                .findFirst();
    }

    public long totalTimeMs() {
        return totalTimeMs;
    }

    public int totalRequests() {
        return results.size();
    }

    public int successfulRequests() {
        return successfulRequests;
    }

    public int failedRequests() {
        return failedRequests;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String terminationReason() {
        return terminationReason;
    }

    public static class Result {
        private final String requestId;
        private final Request request;
        private final Response response;
        private final Throwable error;
        private final long requestTimeMs;
        private final boolean success;
        private final boolean critical;

        public Result(String requestId, Request request, Response response,
                     Throwable error, long requestTimeMs, boolean success, boolean critical) {
            this.requestId = requestId;
            this.request = request;
            this.response = response;
            this.error = error;
            this.requestTimeMs = requestTimeMs;
            this.success = success;
            this.critical = critical;
        }

        public String requestId() {
            return requestId;
        }

        public Request request() {
            return request;
        }

        public Optional<Response> response() {
            return Optional.ofNullable(response);
        }

        public Optional<Throwable> error() {
            return Optional.ofNullable(error);
        }

        public long requestTimeMs() {
            return requestTimeMs;
        }

        public boolean success() {
            return success;
        }

        public boolean isCritical() {
            return critical;
        }

        public static Result success(String requestId, Request request, Response response, long requestTimeMs, boolean critical) {
            return new Result(requestId, request, response, null, requestTimeMs, true, critical);
        }

        public static Result failure(String requestId, Request request, Throwable error, long requestTimeMs, boolean critical) {
            return new Result(requestId, request, null, error, requestTimeMs, false, critical);
        }
    }

    public static Builder builder(String batchId) {
        return new Builder(batchId);
    }

    public static class Builder {
        private final String batchId;
        private List<Result> results = new ArrayList<>();
        private long totalTimeMs;
        private boolean completed;
        private boolean cancelled;
        private String terminationReason;

        Builder(String batchId) {
            this.batchId = batchId;
        }

        public Builder addResult(Result result) {
            results.add(result);
            return this;
        }

        public Builder totalTimeMs(long totalTimeMs) {
            this.totalTimeMs = totalTimeMs;
            return this;
        }

        public Builder completed(boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder cancelled(boolean cancelled) {
            this.cancelled = cancelled;
            return this;
        }

        public Builder terminationReason(String terminationReason) {
            this.terminationReason = terminationReason;
            return this;
        }

        public BatchResult build() {
            return new BatchResult(batchId, results, totalTimeMs, completed, cancelled, terminationReason);
        }
    }
}