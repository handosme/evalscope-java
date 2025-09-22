# EvalScope Java 8 Perf 性能压测使用指南

本指南基于 [EvalScope官方文档](https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html) 参数规范，提供Java 8环境下的perf性能分析解决方案。

## 🚀 快速开始

### 基本使用方式

```bash
# 运行完整的Java 8 perf压测流程
cd /Users/kc/kc/dev/ClaudeCode/evalscope-java
./examples/run-java8-perf-stress.sh

# 跳过编译步骤（快速重跑）
./examples/run-java8-perf-stress.sh --skip-compilation

# 查看帮助信息
./examples/run-java8-perf-stress.sh --help
```

### 直接运行Java压测示例

```bash
# 编译Java代码
javac -d build/perf-stress examples/java8-perf-stress-example.java

# 基础压测
java -cp build/perf-stress Java8PerfStressExample

# 自定义参数压测
java -cp build/perf-stress Java8PerfStressExample \
    --concurrent 100 \
    --number 2000 \
    --max-tokens 2048 \
    --requests-per-second 50 \
    --output results/custom/
```

## 📋 参数配置（遵循EvalScope文档规范）

### 基础压测参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `--concurrent` | Integer | 50 | 并发线程数 |
| `--number` | Integer | 200 | 总请求数 |
| `--max-tokens` | Integer | 1024 | 最大token数 |
| `--requests-per-second` | Integer | 25 | 每秒请求数限制 |
| `--output` | String | results/perf-stress-java8/ | 输出目录 |

### EvalScope兼容参数

对应官方文档中的参数实现：

```yaml
# 应力测试核心参数（遵循官方文档规范）
evaluations:
  perf_cpu_stress:
    parameters:
      max_examples: 1000        # --number 参数
      concurrent: 50           # --concurrent 参数
      max_tokens: 256          # --max-tokens 参数
      requests_per_second: 25  # 速率限制

      # EvalScope特有的perf事件
      perf_events: "cpu-clock,instructions,cache-misses,branch-misses"
      perf_sampling_frequency: 99  # 采样频率Hz
```

## 🔧 高级功能

### 1. 系统级性能分析（需要Linux + perf）

```bash
# 基础perf分析
perf stat -e cpu-clock,instructions,cache-misses,branch-misses \
  java -cp build/perf-stress Java8PerfStressExample

# 调用图分析
perf record -F 99 -g \
  java -cp build/perf-stress Java8PerfStressExample

# 生成火焰图（需要FlameGraph工具）
perf script | ~/FlameGraph/stackcollapse-perf.pl | ~/FlameGraph/flamegraph.pl \
  --title "EvalScope CPU Stress" --width 1200 > flamegraph.svg
```

### 2. JVM内核性能监控

```bash
# 运行时JVM监控
jcmd <PID> VM.system_properties

# GC统计监控
jcmd <PID> GC.heap_info
jcmd <PID> GC.class_histogram

# 内存分配分析
jcmd <PID> VM.native_memory summary

# JIT编译器分析
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining \
     -XX:+PrintCompilation \
     Java8PerfStressExample
```

### 3. 多维度性能测试模式

#### CPU密集型测试
```java
// CPU模式：斐波那契计算 + 数组排序 + 哈希计算
java -cp build/perf-stress Java8PerfStressExample --type cpu
```

#### 内存密集测试
```java
// 内存模式：大量对象分配 + GC压力测试
java -cp build/perf-stress Java8PerfStressExample --type memory
```

#### 综合压力测试
```java
// 综合模式：混合CPU/内存/I/O负载
java -cp build/perf-stress Java8PerfStressExample --type comprehensive
```

## 📊 性能指标解读

### CPU性能指标

| 指标 | 优秀值 | 需要优化 | 说明 |
|------|--------|----------|------|
| CPI (Cycles Per Instruction) | < 1.0 | > 2.0 | CPU执行效率 |
| Cache Miss Rate | < 5% | > 10% | 缓存命中率 |
| Branch Pred Fail Rate | < 2% | > 5% | 分支预测准确性 |
| Context Switch Rate | < 1K/s | > 10K/s | 上下文切换开销 |

### 内存性能指标

| 指标 | 优秀值 | 需要优化 | 说明 |
|------|--------|----------|------|
| Page Fault Rate | 越低越好 | > 500/s | 页面错误频率 |
| Cache Miss Rate | < 5% | > 15% | L1/L2/LLC缓存未命中率 |
| GC Time Ratio | < 5% | > 10% | GC时间占比 |
| Memory Bandwidth Util | < 60% | > 80% | 内存带宽利用率 |

### 系统级指标

| 指标 | 优秀值 | 需要优化 | 说明 |
|------|--------|----------|------|
| Success Rate | > 99% | < 95% | 请求成功率 |
| Avg Latency (P50) | < 100ms | > 500ms | 平均响应时间 |
| P95 Latency | < 200ms | > 1000ms | 95分位数延迟 |
| P99 Latency | < 500ms | > 2000ms | 99分位数延迟 |

## 🎯 实战示例

### 示例1：性能回归测试

```bash
#!/bin/bash

# 建立性能基线
echo "建立性能基线..."
java -cp build/perf-stress Java8PerfStressExample \
    --concurrent 50 --number 1000 \
    --output results/baseline/

# 运行优化版本测试
echo "运行优化版本..."
java -cp build/perf-stress Java8PerfStressExample \
    --concurrent 50 --number 1000 \
    --output results/optimized/

# 对比分析
python analyze_performance_delta.py \
    results/baseline/comprehensive_stress_results.json \
    results/optimized/comprehensive_stress_results.json
```

### 示例2：容量规划测试

```bash
#!/bin/bash

# 单机容量测试：并发递增模式
for concurrent in 10 25 50 100 200; do
    echo "测试并发: $concurrent"
    java -cp build/perf-stress Java8PerfStressExample \
        --concurrent $concurrent \
        --number 2000 \
        --requests-per-minute -1 \\\n        --output "results/capacity/c${concurrent}/"
done

# 生成容量曲线图
gnuplot -e "set terminal png; set output 'capacity-curve.png'; \
    plot 'results/capacity/throughput.csv' with lines"
```

### 示例3：可靠性测试

```bash
#!/bin/bash

# 长时间稳定性测试
for hour in {1..24}; do
    echo "小时 $hour - 长期稳定性测试"
    java -cp build/perf-stress Java8PerfStressExample \
        --concurrent 30 --number 3600 \
        --requests-per-second 10 \\\n        --output "results/longevity/hour${hour}/" &

    sleep 3600  # 每小时轮换
done

# 分析错误恢复情况
grep -r "error_rate" results/longevity/ | \
  awk -F: '{print $1 " " $2}' | sort -k2 -n
```

## 🔍 故障排查

### 常见问题1：perf工具不可用

```bash
# 检查系统兼容性
uname -a
ls -la /usr/bin/perf

# 安装perf工具
# Ubuntu/Debian:
sudo apt-get install linux-tools-common linux-tools-$(uname -r)

# RHEL/CentOS:
sudo yum install perf

# 权限问题解决
echo -1 | sudo tee /proc/sys/kernel/perf_event_paranoid
```

### 常见问题2：Java版本不匹配

```bash
# 检查当前Java版本
java -version
javac -version

# 安装Java 8
sudo apt-get install openjdk-8-jdk
# 或者
sudo yum install java-1.8.0-openjdk-devel

# 配置环境变量
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### 常见问题3：内存不足

```bash
# 检查内存使用情况
free -h
top -p <PID>

# 调整JVM堆大小
export JAVA_OPTS="-Xms2g -Xmx4g"

# 添加GC监控
export JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
```

## 📚 延伸阅读

### 官方文档参考
- [EvalScope应力测试参数](https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html)
- [Linux perf工具手册](https://perf.wiki.kernel.org/index.php/Main_Page)
- [Java性能调优指南](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/performance-enhancements-7.html)

### 性能分析工具
- [perf Examples](https://github.com/brendangregg/perf-tools)
- [FlameGraph工具集](https://github.com/brendangregg/FlameGraph)
- [JDK Mission Control](https://wiki.openjdk.java.net/display/jmc)

### 性能调优实践
- [阿里巴巴Java开发手册](https://github.com/alibaba/p3c)
- [Java Performance Tuning Guide](https://java-performance.com/)
- [系统性能调优要点](https://www.brendangregg.com/linuxperf.html)

## 💡 结语

本Java 8 Perf压测方案基于EvalScope官方文档参数规范，提供了：

1. **兼容性强**：纯Java 8实现，无额外依赖
2. **功能完整**：支持CPU/内存/I/O全方位性能分析
3. **系统级分析**：整合Linux perf工具进行深度性能剖析
4. **易用性高**：提供可视化脚本和详细使用文档
5. **扩展性好**：模块化设计，便于定制化开发

通过这套工具，您可以：
- 建立性能基线并监控性能回归
- 快速定位和解决性能瓶颈
- 进行容量规划和系统调优
- 验证架构设计的性能表现

---

*本指南遵循EvalScope官方文档的API设计原则，确保与官方工具链的兼容性和一致性。*