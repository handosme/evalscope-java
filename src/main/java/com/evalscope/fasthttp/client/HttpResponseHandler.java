package com.evalscope.fasthttp.client;

import com.evalscope.fasthttp.http.*;
import com.evalscope.fasthttp.utils.HttpEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final Request request;
    private final CompletableFuture<Response> future;
    private final long startTime;
    private final StringBuilder responseBody = new StringBuilder();
    private Response.Builder responseBuilder;
    private HttpResponse httpResponse;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public HttpResponseHandler(Request request, CompletableFuture<Response> future) {
        this.request = request;
        this.future = future;
        this.startTime = System.currentTimeMillis();
        this.future.whenComplete((response, throwable) -> {
            if (response == null || throwable != null) {
                cancelled.set(true);
            }
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (cancelled.get()) {
            ctx.close();
            return;
        }

        // Build and send HTTP request
        FullHttpRequest httpRequest = HttpEncoder.encodeRequest(request);
        ctx.writeAndFlush(httpRequest);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (cancelled.get()) {
            ctx.close();
            return;
        }

        if (msg instanceof HttpResponse) {
            httpResponse = (HttpResponse) msg;
            responseBuilder = new Response.Builder()
                    .request(request)
                    .code(httpResponse.status().code())
                    .message(httpResponse.status().reasonPhrase())
                    .elapsedTimeMs(System.currentTimeMillis() - startTime);

            // Process headers
            for (String name : httpResponse.headers().names()) {
                for (String value : httpResponse.headers().getAll(name)) {
                    responseBuilder.addHeader(name, value);
                }
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf content = httpContent.content();
            if (content.isReadable()) {
                responseBody.append(content.toString(StandardCharsets.UTF_8));
            }

            if (msg instanceof LastHttpContent) {
                completeResponse();
            }
        }
    }

    private void completeResponse() {
        if (responseBuilder != null) {
            Response response = responseBuilder
                    .body(responseBody.toString())
                    .elapsedTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            future.complete(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cancelled.get() && !future.isDone()) {
            future.completeExceptionally(cause);
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!cancelled.get() && !future.isDone()) {
            future.completeExceptionally(new io.netty.handler.timeout.ReadTimeoutException());
        }
    }
}