package com.evalscope.fasthttp.utils;

import com.evalscope.fasthttp.http.Headers;
import com.evalscope.fasthttp.http.Request;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;

import java.net.URI;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class HttpEncoder {

    public static FullHttpRequest encodeRequest(Request request) {
        URI uri = URI.create(request.url());
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }

        HttpMethod method = getHttpMethod(request.method());
        ByteBuf content = request.body() != null
                ? Unpooled.wrappedBuffer(request.body().getBytes())
                : Unpooled.EMPTY_BUFFER;

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                HTTP_1_1,
                method,
                path,
                content
        );

        // Set headers
        for (java.util.Map.Entry<String, java.util.List<String>> entry : request.headers()) {
            String headerName = entry.getKey();
            for (String headerValue : entry.getValue()) {
                httpRequest.headers().add(headerName, headerValue);
            }
        }

        // Set required headers
        if (!httpRequest.headers().contains(HttpHeaderNames.HOST)) {
            httpRequest.headers().set(HttpHeaderNames.HOST, uri.getHost());
        }

        if (!httpRequest.headers().contains(HttpHeaderNames.CONNECTION)) {
            httpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        if (request.body() != null && !httpRequest.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        if (request.body() != null && !httpRequest.headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
            httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
        }

        return httpRequest;
    }

    private static HttpMethod getHttpMethod(String method) {
        switch (method.toUpperCase()) {
            case "GET": return GET;
            case "POST": return POST;
            case "PUT": return PUT;
            case "DELETE": return DELETE;
            case "HEAD": return HEAD;
            case "OPTIONS": return OPTIONS;
            case "PATCH": return PATCH;
            case "TRACE": return TRACE;
            case "CONNECT": return CONNECT;
            default: return new HttpMethod(method);
        }
    }

    public static String extractHost(String url) {
        if (url == null) return null;

        int protocolEnd = url.indexOf("://");
        if (protocolEnd != -1) {
            url = url.substring(protocolEnd + 3);
        }

        int pathStart = url.indexOf('/');
        if (pathStart != -1) {
            url = url.substring(0, pathStart);
        }

        int portIndex = url.indexOf(':');
        if (portIndex != -1) {
            url = url.substring(0, portIndex);
        }

        return url;
    }

    public static int extractPort(String url, String protocol) {
        if (url == null) return -1;

        int protocolEnd = url.indexOf("://");
        if (protocolEnd != -1) {
            url = url.substring(protocolEnd + 3);
        }

        int pathStart = url.indexOf('/');
        if (pathStart != -1) {
            url = url.substring(0, pathStart);
        }

        int portIndex = url.indexOf(':');
        if (portIndex != -1) {
            try {
                return Integer.parseInt(url.substring(portIndex + 1));
            } catch (NumberFormatException e) {
                return getDefaultPort(protocol);
            }
        }

        return getDefaultPort(protocol);
    }

    private static int getDefaultPort(String protocol) {
        if ("https".equalsIgnoreCase(protocol)) {
            return 443;
        } else if ("http".equalsIgnoreCase(protocol)) {
            return 80;
        }
        return -1;
    }
}