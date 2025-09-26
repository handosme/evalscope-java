package com.evalscope.fasthttp.http;

import java.util.*;

public class Request {
    private final String url;
    private final String method;
    private final Headers headers;
    private final String body;
    private final int timeoutMillis;

    private Request(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = new Headers(builder.headers);
        this.body = builder.body;
        this.timeoutMillis = builder.timeoutMillis;
    }

    public String url() {
        return url;
    }

    public String method() {
        return method;
    }

    public Headers headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    public static class Builder {
        private String url;
        private String method = "GET";
        private Map<String, List<String>> headers = new HashMap<>();
        private String body;
        private int timeoutMillis = 30000; // Default 30 seconds

        public Builder url(String url) {
            Objects.requireNonNull(url, "url == null");
            this.url = url;
            return this;
        }

        public Builder get() {
            return method("GET");
        }

        public Builder post() {
            return method("POST");
        }

        public Builder put() {
            return method("PUT");
        }

        public Builder delete() {
            return method("DELETE");
        }

        public Builder methods(String method) {
            Objects.requireNonNull(method, "method == null");
            this.method = method;
            return this;
        }

        private Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(value, "value == null");
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder addHeaderLine(String line) {
            Objects.requireNonNull(line, "line == null");
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                addHeader(parts[0].trim(), parts[1].trim());
            }
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder timeout(int timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis <= 0");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Request build() {
            if (url == null) {
                throw new IllegalStateException("url == null");
            }
            return new Request(this);
        }
    }

    public Builder newBuilder() {
        Builder builder = new Builder().url(url).method(method).body(body).timeout(timeoutMillis);
        for (Map.Entry<String, List<String>> header : headers) {
            for (String value : header.getValue()) {
                builder.addHeader(header.getKey(), value);
            }
        }
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }
}