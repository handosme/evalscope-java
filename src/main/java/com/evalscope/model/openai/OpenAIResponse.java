package com.evalscope.model.openai;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API Chat Completion Response Model
 */
public class OpenAIResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private String system_fingerprint;
    private List<Choice> choices;
    private Usage usage;

    // Constructors
    public OpenAIResponse() {}

    public OpenAIResponse(String id, String object, long created, String model) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
    }

    // Choice inner class
    public static class Choice {
        private Integer index;
        private Message message;
        private Message delta; // for streaming
        private String finish_reason;
        private Object logprobs;

        public Choice() {}

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }

        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }

        public Message getDelta() { return delta; }
        public void setDelta(Message delta) { this.delta = delta; }

        public String getFinish_reason() { return finish_reason; }
        public void setFinish_reason(String finish_reason) { this.finish_reason = finish_reason; }

        public Object getLogprobs() { return logprobs; }
        public void setLogprobs(Object logprobs) { this.logprobs = logprobs; }

        public static class Message {
            private String role;
            private String content;
            private List<ToolCall> tool_calls;

            public Message() {}

            public String getRole() { return role; }
            public void setRole(String role) { this.role = role; }

            public String getContent() { return content; }
            public void setContent(String content) { this.content = content; }

            public List<ToolCall> getTool_calls() { return tool_calls; }
            public void setTool_calls(List<ToolCall> tool_calls) { this.tool_calls = tool_calls; }

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
    }

    // Usage inner class
    public static class Usage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;
        private Map<String, Object> completion_tokens_details;
        private Map<String, Object> prompt_tokens_details;

        public Usage() {}

        public Integer getPrompt_tokens() { return prompt_tokens; }
        public void setPrompt_tokens(Integer prompt_tokens) { this.prompt_tokens = prompt_tokens; }

        public Integer getCompletion_tokens() { return completion_tokens; }
        public void setCompletion_tokens(Integer completion_tokens) { this.completion_tokens = completion_tokens; }

        public Integer getTotal_tokens() { return total_tokens; }
        public void setTotal_tokens(Integer total_tokens) { this.total_tokens = total_tokens; }

        public Map<String, Object> getCompletion_tokens_details() { return completion_tokens_details; }
        public void setCompletion_tokens_details(Map<String, Object> completion_tokens_details) { this.completion_tokens_details = completion_tokens_details; }

        public Map<String, Object> getPrompt_tokens_details() { return prompt_tokens_details; }
        public void setPrompt_tokens_details(Map<String, Object> prompt_tokens_details) { this.prompt_tokens_details = prompt_tokens_details; }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSystem_fingerprint() { return system_fingerprint; }
    public void setSystem_fingerprint(String system_fingerprint) { this.system_fingerprint = system_fingerprint; }

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }
}