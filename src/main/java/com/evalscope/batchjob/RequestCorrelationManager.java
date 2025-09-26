package com.evalscope.batchjob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages correlation between requests and responses for batch operations
 */
public class RequestCorrelationManager {

    /**
     * Represents an outstanding request with its metadata
     */
    public static class OutstandingRequest {
        private final String batchId;
        private final String requestId;
        private final String externalRequestId;
        private final String internalRequestId;
        private final long requestTimestamp;

        public OutstandingRequest(String batchId, String requestId, String externalRequestId, String internalRequestId) {
            this.batchId = batchId;
            this.requestId = requestId;
            this.externalRequestId = externalRequestId;
            this.internalRequestId = internalRequestId;
            this.requestTimestamp = System.currentTimeMillis();
        }

        public String getBatchId() {
            return batchId;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getExternalRequestId() {
            return externalRequestId;
        }

        public String getInternalRequestId() {
            return internalRequestId;
        }

        public long getRequestTimestamp() {
            return requestTimestamp;
        }
    }

    private final Map<String, OutstandingRequest> outstandingRequests = new ConcurrentHashMap<>();

    /**
     * Register a new request and return a correlation ID
     */
    public String registerRequest(String batchId, com.evalscope.batchjob.model.BatchRequest request) {
        String internalRequestId = UUID.randomUUID().toString();
        String requestId = request.getRequestId() != null ? request.getRequestId() : internalRequestId;
        OutstandingRequest outstandingRequest = new OutstandingRequest(batchId, requestId, requestId, internalRequestId);
        outstandingRequests.put(internalRequestId, outstandingRequest);
        return internalRequestId;
    }

    /**
     * Complete an outstanding request and return it
     */
    public OutstandingRequest completeRequest(String internalRequestId) {
        return outstandingRequests.remove(internalRequestId);
    }

    /**
     * Find an outstanding request by external ID
     */
    public OutstandingRequest findOutstandingRequestByExternalId(String externalRequestId) {
        return outstandingRequests.values().stream()
                .filter(req -> externalRequestId.equals(req.getExternalRequestId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the number of outstanding requests
     */
    public int getOutstandingRequestCount() {
        return outstandingRequests.size();
    }
}