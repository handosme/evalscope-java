package com.evalscope.cli;

/**
 * EvalScope Command Line Arguments Parser
 * Translates all EvalScope parameters from the official documentation
 */
public class CommandLineArgs {

    // Basic test parameters
    private String url;
    private String model;
    private String apiKey;
    private String dataset;
    private String apiBaseUrl;

    // Performance test parameters
    private Integer concurrent = 1;
    private Integer number = 1;
    private Integer rounds = 1;

    // Request parameters
    private Integer maxTokens = 2048;
    private Double temperature = 0.7;
    private Double topP = 0.9;
    private Double frequencyPenalty = 0.0;
    private Double presencePenalty = 0.0;
    private String stop;
    private Boolean stream = false;
    private String systemPrompt;

    // Connection pool parameters
    private Integer maxWorkers = 10;
    private Integer connectTimeout = 30;
    private Integer readTimeout = 60;

    // Retry and error handling
    private Integer maxRetries = 3;
    private Integer retryDelay = 1000;

    // Test mode parameters
    private Boolean debug = false;
    private Boolean dryRun = false;
    private Boolean verbose = false;

    // Output parameters
    private String outputPath;
    private String outputFormat = "json";
    private Boolean saveResults = true;

    // Dataset parameters
    private String datasetPath;
    private Integer datasetLimit;
    private Boolean datasetShuffle = false;
    // Line-by-line dataset specific parameters
    private Integer maxExamples;
    private Integer skipLines = 0;
    private String linePrefix;

    // Evaluation parameters
    private String evaluationType = "standard";
    private String metrics;
    private Boolean includeLatency = true;
    private Boolean includeAccuracy = true;

    // Authentication parameters
    private String authType;
    private String authToken;

    // Rate limiting parameters
    private Integer requestsPerSecond;
    private Integer requestsPerMinute;

    // System parameters
    private String configFile;
    private String logLevel = "INFO";
    private Boolean help = false;
    private String version;

    // Constructor
    public CommandLineArgs() {}

    // Getters and setters for all parameters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }

    public Integer getConcurrent() { return concurrent; }
    public void setConcurrent(Integer concurrent) { this.concurrent = concurrent; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public Integer getRounds() { return rounds; }
    public void setRounds(Integer rounds) { this.rounds = rounds; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }

    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }

    public String getStop() { return stop; }
    public void setStop(String stop) { this.stop = stop; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Integer getMaxWorkers() { return maxWorkers; }
    public void setMaxWorkers(Integer maxWorkers) { this.maxWorkers = maxWorkers; }

    public Integer getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Integer connectTimeout) { this.connectTimeout = connectTimeout; }

    public Integer getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Integer readTimeout) { this.readTimeout = readTimeout; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public Integer getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Integer retryDelay) { this.retryDelay = retryDelay; }

    public Boolean getDebug() { return debug; }
    public void setDebug(Boolean debug) { this.debug = debug; }

    public Boolean getDryRun() { return dryRun; }
    public void setDryRun(Boolean dryRun) { this.dryRun = dryRun; }

    public Boolean getVerbose() { return verbose; }
    public void setVerbose(Boolean verbose) { this.verbose = verbose; }

    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public Boolean getSaveResults() { return saveResults; }
    public void setSaveResults(Boolean saveResults) { this.saveResults = saveResults; }

    public String getDatasetPath() { return datasetPath; }
    public void setDatasetPath(String datasetPath) { this.datasetPath = datasetPath; }

    public Integer getDatasetLimit() { return datasetLimit; }
    public void setDatasetLimit(Integer datasetLimit) { this.datasetLimit = datasetLimit; }

    public Boolean getDatasetShuffle() { return datasetShuffle; }
    public void setDatasetShuffle(Boolean datasetShuffle) { this.datasetShuffle = datasetShuffle; }

    public Integer getMaxExamples() { return maxExamples; }
    public void setMaxExamples(Integer maxExamples) { this.maxExamples = maxExamples; }

    public Integer getSkipLines() { return skipLines; }
    public void setSkipLines(Integer skipLines) { this.skipLines = skipLines; }

    public String getLinePrefix() { return linePrefix; }
    public void setLinePrefix(String linePrefix) { this.linePrefix = linePrefix; }

    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String evaluationType) { this.evaluationType = evaluationType; }

    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }

    public Boolean getIncludeLatency() { return includeLatency; }
    public void setIncludeLatency(Boolean includeLatency) { this.includeLatency = includeLatency; }

    public Boolean getIncludeAccuracy() { return includeAccuracy; }
    public void setIncludeAccuracy(Boolean includeAccuracy) { this.includeAccuracy = includeAccuracy; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public Integer getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(Integer requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }

    public Integer getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(Integer requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }

    public String getConfigFile() { return configFile; }
    public void setConfigFile(String configFile) { this.configFile = configFile; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public Boolean getHelp() { return help; }
    public void setHelp(Boolean help) { this.help = help; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    @Override
    public String toString() {
        return "CommandLineArgs{" +
                "url='" + url + '\'' +
                ", model='" + model + '\'' +
                ", apiKey='" + apiKey + '\'' +
                ", dataset='" + dataset + '\'' +
                ", concurrent=" + concurrent +
                ", number=" + number +
                ", rounds=" + rounds +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", frequencyPenalty=" + frequencyPenalty +
                ", presencePenalty=" + presencePenalty +
                ", stream=" + stream +
                ", maxWorkers=" + maxWorkers +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", debug=" + debug +
                ", dryRun=" + dryRun +
                ", outputPath='" + outputPath + '\'' +
                ", outputFormat='" + outputFormat + '\'' +
                ", saveResults=" + saveResults +
                ", evaluationType='" + evaluationType + '\'' +
                ", logLevel='" + logLevel + '\'' +
                '}';
    }
}