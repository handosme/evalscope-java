|import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * EvalScope Java 8 Perf性能压测示例
 * 遵循 https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html 参数规范
 */
public class Java8PerfStressExample {

    // 性能监控指标
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong successfulRequests = new AtomicLong(0);
    private static final AtomicLong failedRequests = new AtomicLong(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Long> latencyHistogram = new ConcurrentHashMap<>();

    // 配置文件路径
    private static final String CONFIG_PATH = "examples/config.perf-stress.yaml";

    // 压测参数（对应EvalScope文档参数）
    private static class StressTestParams {
        int maxExamples = 1000;
        int number = 200;
        int rounds = 3;
        int concurrent = 50;
        int maxWorkers = 100;
        int maxTokens = 1024;
        int requestsPerSecond = 25;
        int requestsPerMinute = 1500;
        int connectTimeout = 30;
        int readTimeout = 60;
        int maxRetries = 3;
        int retryDelay = 1000;

        // CPU perf参数
        Set<String> perfEvents = new HashSet<>(Arrays.asList(
            "cpu-clock", "instructions", "cache-misses", "branch-misses"
        ));
        int perfSamplingFreq = 99;
        boolean perfCallGraph = true;

        // 输出配置
        String outputPath = "results/perf-stress-java8/";
        String resultFormat = "json";
        boolean saveResults = true;
        boolean verbose = true;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== EvalScope Java 8 Perf 性能压测 ===");
        System.out.println("模式: 高性能CPU分析 + 内存监控");
        System.out.println("参考文档: https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html");

        StressTestParams params = new StressTestParams();

        // 解析命令行参数
        parseCommandLineArgs(args, params);

        // 创建输出目录
        new File(params.outputPath).mkdirs();

        // 运行不同模式的perf压测
        runPerfCpuStress(params);
        runPerfMemoryStress(params);
        runPerfComprehensiveStress(params);

        // 生成测试报告
        generatePerfReport(params);
    }

    /**
     * CPU性能压测分析
     * 对应 perf_cpu_stress 评估类型
     */
    private static void runPerfCpuStress(StressTestParams params) throws Exception {
        System.out.println("\n=== 运行 CPU 性能压测 ===");

        ExecutorService executor = Executors.newFixedThreadPool(params.concurrent);
        CountDownLatch latch = new CountDownLatch(params.number);

        long startTime = System.currentTimeMillis();

        // 创建Perf事件监听器（模拟）
        PerfEventMonitor perfMonitor = new PerfEventMonitor(params.perfEvents);
        perfMonitor.start();

        // 启动压测任务
        for (int i = 0; i < params.number; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 模拟CPU密集型操作
                    performCpuIntensiveTask(requestId);

                    successfulRequests.incrementAndGet();
                    recordLatency(100); // 模拟延迟

                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                    if (params.verbose) {
                        System.err.println("Request " + requestId + " failed: " + e.getMessage());
                    }
                } finally {
                    totalRequests.incrementAndGet();
                    latch.countDown();
                }
            });

            // 控制发送速率
            controlRequestRate(params, i);
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        perfMonitor.stop();

        // 保存结果
        Map<String, Object> results = generateResults("cpu_stress", startTime, endTime, perfMonitor);
        saveResults(results, params.outputPath + "cpu_stress_results.json");

        System.out.println("✓ CPU压测完成，总耗时: " + (endTime - startTime) + "ms");
        System.out.println("  请求数: " + totalRequests.get() + ", 成功率: " +
                          String.format("%.2f%%", (successfulRequests.get() * 100.0 / totalRequests.get())));
    }

    /**
     * 内存性能压测分析
     * 对应 perf_memory_stress 评估类型
     */
    private static void runPerfMemoryStress(StressTestParams params) throws Exception {
        System.out.println("\n=== 运行 内存 性能压测 ===");

        ExecutorService executor = Executors.newFixedThreadPool(params.concurrent / 2);
        CountDownLatch latch = new CountDownLatch(params.number / 2);

        long startTime = System.currentTimeMillis();

        // 创建内存事件监听器
        PerfEventMonitor memoryMonitor = new PerfEventMonitor(
            new HashSet<>(Arrays.asList("cache-misses", "page-faults", "major-faults", "minor-faults"))
        );
        memoryMonitor.start();

        // 启动内存压测任务
        for (int i = 0; i < params.number / 2; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 模拟内存分配密集型操作
                    performMemoryIntensiveTask(requestId);

                    successfulRequests.incrementAndGet();
                    recordLatency(200); // 模拟更大延迟

                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                    if (params.verbose) {
                        System.err.println("Memory request " + requestId + " failed: " + e.getMessage());
                    }
                } finally {
                    totalRequests.incrementAndGet();
                    latch.countDown();
                }
            });

            Thread.sleep(10); // 控制内存分配速率
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        memoryMonitor.stop();

        // 触发垃圾回收并收集统计信息
        System.gc();
        Thread.sleep(1000);

        // 保存结果
        Map<String, Object> results = generateResults("memory_stress", startTime, endTime, memoryMonitor);
        results.put("gc_stats", collectGCStats());
        saveResults(results, params.outputPath + "memory_stress_results.json");

        System.out.println("✓ 内存压测完成，总耗时: " + (endTime - startTime) + "ms");
        System.out.println("  JVM内存使用: " + getMemoryUsage());
    }

    /**
     * 综合性能压测分析
     * 对应 perf_comprehensive_stress 评估类型
     */
    private static void runPerfComprehensiveStress(StressTestParams params) throws Exception {
        System.out.println("\n=== 运行 综合 性能压测 ===");

        int finalConcurrent = Math.min(params.concurrent * 2, 100); // 最高100并发
        ExecutorService executor = Executors.newFixedThreadPool(finalConcurrent);
        CountDownLatch latch = new CountDownLatch(params.number);

        long startTime = System.currentTimeMillis();

        // 创建综合事件监听器
        Set<String> comprehensiveEvents = new HashSet<>();
        comprehensiveEvents.addAll(params.perfEvents);
        comprehensiveEvents.addAll(Arrays.asList("task-clock", "page-faults", "context-switches"));

        PerfEventMonitor comprehensiveMonitor = new PerfEventMonitor(comprehensiveEvents);
        comprehensiveMonitor.start();

        // 启动混合工作负载压测
        for (int i = 0; i < params.number; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 根据请求ID随机选择不同类型的负载
                    if (requestId % 3 == 0) {
                        performCpuIntensiveTask(requestId);
                    } else if (requestId % 3 == 1) {
                        performMemoryIntensiveTask(requestId);
                    } else {
                        performIoIntensiveTask(requestId);
                    }

                    successfulRequests.incrementAndGet();
                    recordLatency(150 + (int)(Math.random() * 100));

                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                } finally {
                    totalRequests.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        comprehensiveMonitor.stop();

        // 保存结果
        Map<String, Object> results = generateResults("comprehensive_stress", startTime, endTime, comprehensiveMonitor);
        saveResults(results, params.outputPath + "comprehensive_stress_results.json");

        System.out.println("✓ 综合压测完成，峰值并发: " + finalConcurrent);

        // 生成火焰图数据（简化版）
        generateFlameGraphData(comprehensiveMonitor, params.outputPath + "flamegraph_data.txt");
    }

    /**
     * 模拟CPU密集型任务
     */
    private static void performCpuIntensiveTask(int requestId) {
        // CPU密集计算：斐波那契数列 + 数组排序 + 哈希计算
        long result = fibonacci(30);

        // 数组排序
        int[] array = new int[1000];
        for (int i = 0; i < array.length; i++) {
            array[i] = (int)(Math.random() * 1000);
        }
        Arrays.sort(array);

        // 字符串哈希计算
        String data = "cpu_intensive_request_" + requestId;
        for (int i = 0; i < 1000; i++) {
            data = String.valueOf(data.hashCode());
        }

        // 模拟网络/系统调用延迟
        try {
            Thread.sleep(50 + (int)(Math.random() * 50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 模拟内存密集型任务
     */
    private static void performMemoryIntensiveTask(int requestId) {
        // 大量内存分配
        List<byte[]> memoryChunks = new ArrayList<>();

        for (int i = 0; i < 50; i++) { // 分配50MB内存
            memoryChunks.add(new byte[1024 * 1024]); // 1MB chunks
        }

        // 复杂对象创建
        List<Map<String, Object>> complexObjects = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> obj = new HashMap<>();
            obj.put("id", i);
            obj.put("data", new byte[1024]);
            obj.put("metadata", "memory_test_request_" + requestId + "_" + i);
            obj.put("timestamp", System.currentTimeMillis());
            complexObjects.add(obj);
        }

        // 延迟处理确保GC工作
        try {
            Thread.sleep(100 + (int)(Math.random() * 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 模拟I/O密集型任务
     */
    private static void performIoIntensiveTask(int requestId) {
        // 模拟网络I/O延迟
        try {
            Thread.sleep(150 + (int)(Math.random() * 300));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 模拟文件I/O操作（简化版）
        String tempDir = System.getProperty("java.io.tmpdir");
        String filename = tempDir + "/evalscope_perf_test_" + requestId + ".tmp";

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("I/O intensive test data for request " + requestId + "\n");
            for (int i = 0; i < 100; i++) {
                writer.write("Line " + i + ": test data\n");
            }
        } catch (IOException e) {
            // 忽略文件I/O错误
        }

        // 清理临时文件
        new File(filename).delete();
    }

    /**
     * 计算斐波那契数列（CPU密集）
     */
    private static long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    /**
     * 记录延迟数据
     */
    private static void recordLatency(long latency) {
        totalLatency.addAndGet(latency);
        long bucket = (latency / 50) * 50; // 50ms分桶
        latencyHistogram.merge(bucket, 1L, Long::sum);
    }

    /**
     * 控制请求发送速率
     */
    private static void controlRequestRate(StressTestParams params, int requestIndex) {
        if (params.requestsPerSecond > 0) {
            int expectedIntervalMs = 1000 / params.requestsPerSecond;
            if (requestIndex % params.requestsPerSecond == 0 && requestIndex > 0) {
                try {
                    Thread.sleep(expectedIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 生成测试结果
     */
    private static Map<String, Object> generateResults(String testType, long startTime,
                                                         long endTime, PerfEventMonitor monitor) {
        Map<String, Object> results = new HashMap<>();

        // 基础指标
        results.put("test_type", testType);
        results.put("start_time", startTime);
        results.put("end_time", endTime);
        results.put("duration_ms", endTime - startTime);
        results.put("total_requests", totalRequests.get());
        results.put("successful_requests", successfulRequests.get());
        results.put("failed_requests", failedRequests.get());
        results.put("success_rate", successfulRequests.get() * 100.0 / totalRequests.get());

        // 延迟指标
        results.put("avg_latency_ms", totalLatency.get() / totalRequests.get());
        results.put("latency_histogram", latencyHistogram);

        // perf事件数据
        results.put("perf_events", monitor.getEventCounts());
        results.put("perf_statistics", monitor.getStatistics());

        // 内存使用
        results.put("memory_usage", getMemoryUsage());

        // 时间戳
        results.put("timestamp", new Date().toString());

        return results;
    }

    /**
     * 获取内存使用情况
     */
    private static Map<String, Object> getMemoryUsage() {
        Map<String, Object> memory = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        memory.put("used_mb", usedMemory / (1024 * 1024));
        memory.put("free_mb", freeMemory / (1024 * 1024));
        memory.put("total_mb", totalMemory / (1024 * 1024));
        memory.put("max_mb", maxMemory / (1024 * 1024));

        return memory;
    }

    /**
     * 收集GC统计信息
     */
    private static List<Map<String, Object>> collectGCStats() {
        List<Map<String, Object>> gcStats = new ArrayList<>();

        for (java.lang.management.GarbageCollectorMXBean gcBean :
             java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {

            Map<String, Object> gcInfo = new HashMap<>();
            gcInfo.put("name", gcBean.getName());
            gcInfo.put("collection_count", gcBean.getCollectionCount());
            gcInfo.put("collection_time_ms", gcBean.getCollectionTime());

            gcStats.add(gcInfo);
        }

        return gcStats;
    }

    /**
     * 保存结果到文件
     */
    private static void saveResults(Map<String, Object> results, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            // 简化的JSON输出（Java 8兼容）
            writer.write("{\n");
            List<String> entries = new ArrayList<>();

            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    entries.add("  \"" + key + "\": \"" + value + "\"");
                } else if (value instanceof Number) {
                    entries.add("  \"" + key + "\": " + value);
                } else if (value instanceof Map) {
                    entries.add("  \"" + key + "\": " + mapToJson((Map<?, ?>) value));
                } else if (value instanceof List) {
                    entries.add("  \"" + key + "\": " + listToJson((List<?>) value));
                }
            }

            writer.write(String.join(",\n", entries));
            writer.write("\n}\n");

        } catch (IOException e) {
            System.err.println("保存结果失败: " + e.getMessage());
        }
    }

    /**
     * 生成简化的火焰图数据
     */
    private static void generateFlameGraphData(PerfEventMonitor monitor, String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# EvalScope Perf Flame Graph Data\n");
            writer.write("# Format: function_name;caller;caller count\n");
            writer.write("performCpuIntensiveTask;main;" + successfulRequests.get() / 3 + "\n");
            writer.write("performMemoryIntensiveTask;main;" + successfulRequests.get() / 3 + "\n");
            writer.write("performIoIntensiveTask;main;" + successfulRequests.get() / 3 + "\n");
            writer.write("fibonacci;performCpuIntensiveTask;" + successfulRequests.get() / 3 + "\n");
        } catch (IOException e) {
            System.err.println("生成火焰图数据失败: " + e.getMessage());
        }
    }

    /**
     * 生成性能报告
     */
    private static void generatePerfReport(StressTestParams params) throws IOException {
        System.out.println("\n=== 生成性能报告 ===");

        String reportFile = params.outputPath + "perf_performance_report.md";
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("# EvalScope Java 8 Perf 性能分析报告\n\n");
            writer.write("**测试时间**: " + new Date().toString() + "\n\n");
            writer.write("**Java版本**: " + System.getProperty("java.version") + "\n\n");
            writer.write("**测试配置**: " + CONFIG_PATH + "\n\n");

            writer.write("## 关键性能指标\n\n");
            writer.write("| 指标 | 值 |\n");
            writer.write("|------|-----|\n");
            writer.write("| 总请求数 | " + totalRequests.get() + " |\n");
            writer.write("| 成功请求数 | " + successfulRequests.get() + " |\n");
            writer.write("| 成功率 | " + String.format("%.2f%%", (successfulRequests.get() * 100.0 / totalRequests.get())) + " |\n");
            writer.write("| 平均延迟 | " + (totalLatency.get() / totalRequests.get()) + "ms |\n");
            writer.write("| 失败请求数 | " + failedRequests.get() + " |\n\n");

            writer.write("## 性能调优建议\n\n");
            writer.write("\n### CPU优化\n");
            writer.write("- 监控CPU热点函数：fibonacci计算、数组排序\n");
            writer.write("- 使用perf record -g进行更详细的调用分析\n");
            writer.write("- 考虑CPU亲和性设置和JVM线程调优\n");
            writer.write("\n### 内存优化\n");
            writer.write("- 监控内存分配频率和GC活动\n");
            writer.write("- 使用对象池减少频繁的对象创建\n");
            writer.write("- 调整JVM堆大小和GC参数\n");
            writer.write("\n### I/O优化\n");
            writer.write("- 异步I/O操作减少阻塞\n");
            writer.write("- 连接池复用和超时优化\n");
            writer.write("\n## 后续分析命令\n\n");
            writer.write("```bash\n");
            writer.write("# 更详细的CPU分析\n");
            writer.write("perf record -F 99 -g -- java Java8PerfStressExample --type=cpu\n\n");
            writer.write("# 内存访问分析\n");
            writer.write("perf stat -e cache-misses,page-faults -- java Java8PerfStressExample --type=memory\n\n");
            writer.write("# 生成火焰图\n");
            writer.write("perf script | stackcollapse-perf.pl | flamegraph.pl > flamegraph.svg\n");
            writer.write("```\n");
        }

        System.out.println("✓ 性能报告已生成: " + reportFile);
    }

    /**
     * 解析命令行参数
     */
    private static void parseCommandLineArgs(String[] args, StressTestParams params) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--concurrent":
                    params.concurrent = Integer.parseInt(args[++i]);
                    break;
                case "--number":
                    params.number = Integer.parseInt(args[++i]);
                    break;
                case "--max-tokens":
                    params.maxTokens = Integer.parseInt(args[++i]);
                    break;
                case "--requests-per-second":
                    params.requestsPerSecond = Integer.parseInt(args[++i]);
                    break;
                case "--output":
                    params.outputPath = args[++i];
                    break;
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
            }
        }
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("Usage: java Java8PerfStressExample [options]");
        System.out.println("Options:");
        System.out.println("  --concurrent <number>          Concurrent threads (default: 50)");
        System.out.println("  --number <count>              Number of requests (default: 200)");
        System.out.println("  --max-tokens <tokens>        Max tokens per request (default: 1024)");
        System.out.println("  --requests-per-second <rate> Requests per second limit (default: 25)");
        System.out.println("  --output <path>              Output directory path (default: results/perf-stress-java8/)");
        System.out.println("  --help                      Show this help message");
    }

    // 简化JSON工具方法
    private static String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        List<String> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Number) {
                entries.add("\"" + entry.getKey() + "\":" + entry.getValue());
            } else {
                entries.add("\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"");
            }
        }
        sb.append(String.join(",", entries)).append("}");
        return sb.toString();
    }

    private static String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        List<String> items = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                items.add(mapToJson((Map<?, ?>) item));
            } else {
                items.add("\"" + item.toString() + "\"");
            }
        }
        sb.append(String.join(",", items)).append("]");
        return sb.toString();
    }
}

/**
 * 模拟的Perf事件监控器（简化版）
 */
class PerfEventMonitor {
    private final Set<String> events;
    private final Map<String, Long> eventCounts = new ConcurrentHashMap<>();
    private boolean running = false;
    private long startTime;
    private long stopTime;

    public PerfEventMonitor(Set<String> events) {
        this.events = events;
        // 初始化事件计数
        events.forEach(event -> eventCounts.put(event, 0L));
    }

    public void start() {
        running = true;
        startTime = System.currentTimeMillis();

        // 模拟事件收集线程
        new Thread(() -> {
            Random random = new Random();
            while (running) {
                try {
                    Thread.sleep(100); // 100ms采样间隔

                    // 模拟事件计数增长
                    for (String event : events) {
                        long increment = random.nextInt(10) + 1; // 随机增量
                        eventCounts.merge(event, increment, Long::sum);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
        stopTime = System.currentTimeMillis();
    }

    public Map<String, Long> getEventCounts() {
        return new HashMap<>(eventCounts);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("duration_ms", stopTime - startTime);
        stats.put("total_events", eventCounts.values().stream().mapToLong(Long::longValue).sum());
        stats.put("events_per_second", eventCounts.values().stream().mapToLong(Long::longValue).sum() * 1000.0 / (stopTime - startTime));
        return stats;
    }
}