package com.evalscope.netty;

import com.evalscope.model.openai.OpenAIRequest;
import com.evalscope.model.openai.OpenAIResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.routing.RoutingHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Mock OpenAI server for testing Netty-based client implementations
 * Supports both standard and streaming (SSE) responses
 */
public class MockOpenAIServer {
    private static final Logger logger = LoggerFactory.getLogger(MockOpenAIServer.class);

    private ServerBootstrap serverBootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final int port;
    private final ObjectMapper objectMapper;

    public MockOpenAIServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        try {
            serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(1048576)); // 1MB
                            pipeline.addLast(new MockOpenAIHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            logger.info("Starting mock OpenAI server on port {}...", port);
            serverChannel = serverBootstrap.bind(port).sync().channel();
            logger.info("Mock OpenAI server started successfully on port {}", port);

        } catch (Exception e) {
            logger.error("Failed to start mock server", e);
            stop();
            throw e;
        }
    }

    public void stop() throws Exception {
        logger.info("Stopping mock OpenAI server...");

        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 1000, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 1000, TimeUnit.MILLISECONDS).awaitUninterruptibly();
        }

        logger.info("Mock OpenAI server stopped");
    }

    /**m
     * Handler for mock OpenAI API requests
     */
    private class MockOpenAIHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            logger.debug("Received {} request to {}", request.method(), request.uri());

            // CORS configuration for browser-based testing
            HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");

            String uri = request.uri();
            HttpMethod method = request.method();

            if ("/v1/chat/completions".equals(uri) && "POST".equals(method.name())) {
                handleChatCompletion(ctx, request);
            } else if ("OPTIONS".equals(method.name())) {
                // Handle CORS preflight
                response.setStatus(HttpResponseStatus.OK);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } else {
                // Handle unknown endpoints
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

                String errorMessage = "Not Found: " + uri;
                FullHttpResponse fullResponse = new DefaultFullHttpResponse(
                    request.protocolVersion(),
                    HttpResponseStatus.NOT_FOUND,
                    Unpooled.copiedBuffer(errorMessage, CharsetUtil.UTF_8)
                );
                fullResponse.headers().add(response.headers());

                ctx.writeAndFlush(fullResponse).addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void handleChatCompletion(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            String requestBody = request.content().toString(CharsetUtil.UTF_8);
            logger.debug("Request body: {}", requestBody);

            // Parse OpenAI request
            OpenAIRequest openAIRequest = objectMapper.readValue(requestBody, OpenAIRequest.class);
            boolean isStreaming = openAIRequest.getStream() != null && openAIRequest.getStream();

            String authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            logger.info("Handling chat completion - streaming: {}", isStreaming);

            if (isStreaming) {
                handleStreamingResponse(ctx, request, openAIRequest);
            } else {
                handleStandardResponse(ctx, request, openAIRequest);
            }
        }

        private void handleStandardResponse(ChannelHandlerContext ctx, FullHttpRequest request, OpenAIRequest openAIRequest) throws Exception {
            // Generate mock response
            OpenAIResponse response = generateMockResponse(openAIRequest, false);

            // Convert to JSON
            String responseJson = objectMapper.writeValueAsString(response);
            logger.debug("Sending standard response: {}", responseJson);

            // Create response
            HttpResponse httpResponse = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            httpResponse.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                    .set(HttpHeaderNames.CONTENT_LENGTH, responseJson.length())
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            FullHttpResponse fullResponse = new DefaultFullHttpResponse(
                    request.protocolVersion(),
                    HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(responseJson, CharsetUtil.UTF_8)
            );
            fullResponse.headers().add(httpResponse.headers());

            ctx.writeAndFlush(fullResponse).addListener(ChannelFutureListener.CLOSE);
        }

        private void handleStreamingResponse(ChannelHandlerContext ctx, FullHttpRequest request, OpenAIRequest openAIRequest) throws Exception {
            // Send response headers
            HttpResponse httpResponse = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            httpResponse.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=UTF-8")
                    .set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(httpResponse);

            // Send streaming events
            String prompt = openAIRequest.getMessages().get(0).getContent();
            String mockResponse = generateMockResponseText(prompt);

            // Split response into chunks for streaming
            String[] words = mockResponse.split(" ");
            int eventId = 1;

            for (int i = 0; i < words.length; i += 2) { // Send 2 words per chunk
                StringBuilder chunkContent = new StringBuilder();
                for (int j = i; j < Math.min(i + 2, words.length); j++) {
                    if (chunkContent.length() > 0) chunkContent.append(" ");
                    chunkContent.append(words[j]);
                }

                String finishReason = (i + 2 >= words.length) ? "stop" : null;

                // Create streaming response chunk
                OpenAIResponse streamResponse = generateMockStreamingResponseChunk(
                    eventId++,
                    openAIRequest.getModel(),
                    chunkContent.toString(),
                    finishReason
                );

                String streamJson = objectMapper.writeValueAsString(streamResponse);
                String sseEvent = String.format("data: %s\n\n", streamJson);

                ctx.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(sseEvent, CharsetUtil.UTF_8)));

                // Add small delay between chunks for realistic streaming effect
                Thread.sleep(100 + ThreadLocalRandom.current().nextInt(200)); // 100-300ms
            }

            // Send completion event
            String doneEvent = "data: [DONE]\n\n";
            ctx.writeAndFlush(new DefaultLastHttpContent(Unpooled.copiedBuffer(doneEvent, CharsetUtil.UTF_8)));
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
        }

        private OpenAIResponse generateMockResponse(OpenAIRequest request, boolean isStreaming) {
            OpenAIResponse response = new OpenAIResponse();
            response.setId("chatcmpl-mock-" + System.currentTimeMillis());
            response.setObject(isStreaming ? "chat.completion.chunk" : "chat.completion");
            response.setCreated(System.currentTimeMillis() / 1000L);
            response.setModel(request.getModel());
            response.setChoices(generateMockChoices(request, null));
            response.setUsage(generateMockUsage());

            return response;
        }

        private OpenAIResponse generateMockStreamingResponseChunk(int eventId, String model, String deltaContent, String finishReason) {
            OpenAIResponse response = new OpenAIResponse();
            response.setId("chatcmpl-mock-" + eventId);
            response.setObject("chat.completion.chunk");
            response.setCreated(System.currentTimeMillis() / 1000L);
            response.setModel(model);
            response.setChoices(generateMockStreamingChoices(deltaContent, finishReason));

            return response;
        }

        private List<OpenAIResponse.Choice> generateMockChoices(OpenAIRequest request, String finishReason) {
            List<OpenAIResponse.Choice> choices = new ArrayList<>();

            for (int i = 0; i < (request.getN() != null ? request.getN() : 1); i++) {
                OpenAIResponse.Choice choice = new OpenAIResponse.Choice();
                choice.setIndex(i);

                String prompt = request.getMessages().get(0).getContent();
                String mockContent = generateMockResponseText(prompt);

                OpenAIResponse.Choice.Message message = new OpenAIResponse.Choice.Message();
                message.setRole("assistant");
                message.setContent(mockContent);

                choice.setMessage(message);
                choice.setFinish_reason(finishReason != null ? finishReason : "stop");

                choices.add(choice);
            }

            return choices;
        }

        private List<OpenAIResponse.Choice> generateMockStreamingChoices(String deltaContent, String finishReason) {
            List<OpenAIResponse.Choice> choices = new ArrayList<>();

            OpenAIResponse.Choice choice = new OpenAIResponse.Choice();
            choice.setIndex(0);

            // For streaming, set delta
            OpenAIResponse.Choice.Message delta = new OpenAIResponse.Choice.Message();
            delta.setContent(deltaContent);
            delta.setRole("assistant");

            choice.setDelta(delta);
            choice.setFinish_reason(finishReason);

            choices.add(choice);
            return choices;
        }

        private String generateMockResponseText(String prompt) {
            // Generate a simple mock response based on the prompt
            if (prompt.toLowerCase().contains("hello") || prompt.toLowerCase().contains("hi")) {
                return "Hello! I'm a mock OpenAI server responding to your greeting. How can I assist you today?";
            }
            else if (prompt.toLowerCase().contains("explain")) {
                return "Here is a simple explanation based on your question. This is generated by the mock server for testing purposes.";
            }
            else if (prompt.toLowerCase().contains("haiku")) {
                return "Spring brings gentle rain,\nNature awakens once more,\nLife begins anew.";
            }
            else {
                return "I received your message: \"" + prompt + "\". This is a mock response generated for testing streaming functionality.";
            }
        }

        private OpenAIResponse.Usage generateMockUsage() {
            OpenAIResponse.Usage usage = new OpenAIResponse.Usage();
            usage.setPrompt_tokens(20 + ThreadLocalRandom.current().nextInt(30));
            usage.setCompletion_tokens(50 + ThreadLocalRandom.current().nextInt(100));
            usage.setTotal_tokens(usage.getPrompt_tokens() + usage.getCompletion_tokens());
            return usage;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Error in mock server handler", cause);
            ctx.close();
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8000;
        MockOpenAIServer server = new MockOpenAIServer(port);

        try {
            server.start();

            // Keep server running
            logger.info("Mock OpenAI server is running on port {}. Press Ctrl+C to stop.", port);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Shutting down mock server...");
                    server.stop();
                } catch (Exception e) {
                    logger.error("Error stopping server", e);
                }
            }));

            // Wait indefinitely (or until interrupted)
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Server failed to start", e);
        }
    }
}