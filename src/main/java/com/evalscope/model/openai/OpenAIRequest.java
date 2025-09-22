package com.evalscope.model.openai;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API Chat Completion Request Model
 */
public class OpenAIRequest {
    private String model;
    private List<Message> messages;
    private Integer max_tokens;
    private Double temperature;
    private Double top_p;
    private Integer n = 1;
    private Boolean stream = false;
    private String stop;
    private Double presence_penalty;
    private Double frequency_penalty;
    private Map<String, Object> user;
    private Integer seed;
    private ResponseFormat response_format;
    private List<String> stop_sequences;

    // Constructors
    public OpenAIRequest() {}

    public OpenAIRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    public OpenAIRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    // Message inner class
    public static class Message {
        private String role;
        private String content;
        private String name;
        private List<ToolCall> tool_calls;
        private String tool_call_id;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<ToolCall> getTool_calls() { return tool_calls; }
        public void setTool_calls(List<ToolCall> tool_calls) { this.tool_calls = tool_calls; }

        public String getTool_call_id() { return tool_call_id; }
        public void setTool_call_id(String tool_call_id) { this.tool_call_id = tool_call_id; }

        public static class ToolCall {
            private String id;
            private String type;
            private Function function;

            public ToolCall() {}

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public Function getFunction() { return function; }
            public void setFunction(Function function) { this.function = function; }

            public static class Function {
                private String name;
                private String arguments;

                public Function() {}

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }

                public String getArguments() { return arguments; }
                public void setArguments(String arguments) { this.arguments = arguments; }
            }
        }
    }

    // ResponseFormat inner class
    public static class ResponseFormat {
        private String type = "text";
        private Object json_schema;

        public ResponseFormat() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Object getJson_schema() { return json_schema; }
        public void setJson_schema(Object json_schema) { this.json_schema = json_schema; }
    }

    // Getters and setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public Integer getMax_tokens() { return max_tokens; }
    public void setMax_tokens(Integer max_tokens) { this.max_tokens = max_tokens; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTop_p() { return top_p; }
    public void setTop_p(Double top_p) { this.top_p = top_p; }

    public Integer getN() { return n; }
    public void setN(Integer n) { this.n = n; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public String getStop() { return stop; }
    public void setStop(String stop) { this.stop = stop; }

    public Double getPresence_penalty() { return presence_penalty; }
    public void setPresence_penalty(Double presence_penalty) { this.presence_penalty = presence_penalty; }

    public Double getFrequency_penalty() { return frequency_penalty; }
    public void setFrequency_penalty(Double frequency_penalty) { this.frequency_penalty = frequency_penalty; }

    public Map<String, Object> getUser() { return user; }
    public void setUser(Map<String, Object> user) { this.user = user; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public ResponseFormat getResponse_format() { return response_format; }
    public void setResponse_format(ResponseFormat response_format) { this.response_format = response_format; }

    public List<String> getStop_sequences() { return stop_sequences; }
    public void setStop_sequences(List<String> stop_sequences) { this.stop_sequences = stop_sequences; }
}