package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.http.Request;
import java.util.*;
import java.util.concurrent.Callable;

public class BatchRequest {
    private final String batchId;
    private final List<RequestWrapper> requests;
    private final int maxConcurrent;
    private final long batchTimeoutMs;
    private final long requestTimeoutMs;

    private BatchRequest(Builder builder) {
        this.batchId = builder.batchId;
        this.requests = Collections.unmodifiableList(new ArrayList<>(builder.requests));
        this.maxConcurrent = builder.maxConcurrent;
        this.batchTimeoutMs = builder.batchTimeoutMs;
        this.requestTimeoutMs = builder.requestTimeoutMs;
    }

    public String batchId() {
        return batchId;
    }

    public List<RequestWrapper> requests() {
        return requests;
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public long batchTimeoutMs() {
        return batchTimeoutMs;
    }

    public long requestTimeoutMs() {
        return requestTimeoutMs;
    }

    public static class RequestWrapper {
        private final String requestId;
        private final Request request;
        private final boolean critical;

        public RequestWrapper(String requestId, Request request, boolean critical) {
            this.requestId = Objects.requireNonNull(requestId, "requestId == null");
            this.request = Objects.requireNonNull(request, "request == null");
            this.critical = critical;
        }

        public String requestId() {
            return requestId;
        }

        public Request request() {
            return request;
        }

        public boolean isCritical() {
            return critical;
        }
    }

    public static class Builder {
        private String batchId = UUID.randomUUID().toString();
        private List<RequestWrapper> requests = new ArrayList<>();
        private int maxConcurrent = 10;
        private long batchTimeoutMs = 60000; // 1 minute
        private long requestTimeoutMs = 30000; // 30 seconds

        public Builder batchId(String batchId) {
            Objects.requireNonNull(batchId, "batchId == null");
            this.batchId = batchId;
            return this;
        }

        public Builder addRequest(Request request) {
            return addRequest(String.valueOf(requests.size()), request, false);
        }

        public Builder addRequest(String requestId, Request request) {
            return addRequest(requestId, request, false);
        }

        public Builder addRequest(String requestId, Request request, boolean critical) {
            Objects.requireNonNull(requestId, "requestId == null");
            Objects.requireNonNull(request, "request == null");
            requests.add(new RequestWrapper(requestId, request, critical));
            return this;
        }

        public Builder maxConcurrent(int maxConcurrent) {
            if (maxConcurrent <= 0) {
                throw new IllegalArgumentException("maxConcurrent <= 0");
            }
            this.maxConcurrent = maxConcurrent;
            return this;
        }

        public Builder batchTimeout(long timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("batchTimeoutMs <= 0");
            }
            this.batchTimeoutMs = timeoutMs;
            return this;
        }

        public Builder requestTimeout(long timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("requestTimeoutMs <= 0");
            }
            this.requestTimeoutMs = timeoutMs;
            return this;
        }

        public BatchRequest build() {
            if (requests.isEmpty()) {
                throw new IllegalStateException("No requests added to batch");
            }
            return new BatchRequest(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}