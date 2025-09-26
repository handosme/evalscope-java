package com.evalscope.fasthttp.http;

import java.io.IOException;
import java.util.Objects;

public class Response {
    private final Request request;
    private final int code;
    private final String message;
    private final Headers headers;
    private final String body;
    private final long elapsedTimeMs;

    private Response(Builder builder) {
        this.request = builder.request;
        this.code = builder.code;
        this.message = builder.message;
        this.headers = new Headers(builder.headers);
        this.body = builder.body;
        this.elapsedTimeMs = builder.elapsedTimeMs;
    }

    public Request request() {
        return request;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Headers headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    public long elapsedTimeMs() {
        return elapsedTimeMs;
    }

    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public static class Builder {
        private Request request;
        private int code;
        private String message;
        private Headers headers = new Headers();
        private String body;
        private long elapsedTimeMs;

        public Builder request(Request request) {
            this.request = request;
            return this;
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder headers(Headers headers) {
            Objects.requireNonNull(headers, "headers == null");
            this.headers = new Headers(headers);
            return this;
        }

        public Builder addHeader(String name, String value) {
            this.headers.newBuilder().add(name, value).build();
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder elapsedTimeMs(long elapsedTimeMs) {
            this.elapsedTimeMs = elapsedTimeMs;
            return this;
        }

        public Response build() {
            if (request == null) {
                throw new IllegalStateException("request == null");
            }
            if (code == 0) {
                throw new IllegalStateException("code == 0");
            }
            return new Response(this);
        }
    }

    public static class ResponseException extends IOException {
        private final Response response;

        public ResponseException(Response response) {
            super("HTTP " + response.code() + " " + response.message());
            this.response = response;
        }

        public ResponseException(Response response, String message) {
            super("HTTP " + response.code() + " " + response.message() + ": " + message);
            this.response = response;
        }

        public Response response() {
            return response;
        }
    }
}