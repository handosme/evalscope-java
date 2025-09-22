#!/bin/bash

# EvalScope Java 8 Perf 性能压测脚本
# 使用JDK 8的jcmd和工具进行性能分析
# 参考: https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html

echo "================================================"
echo "EvalScope Java 8 Perf 性能压测脚本"
echo "遵循 EvalScope 官方文档参数规范"
echo "================================================"
echo

# 设置环境变量
export JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+PreserveFramePointer"
export PERF_OUTPUT_DIR="perf-results/java8-stress"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查Java版本
check_java_version() {
    echo -e "${BLUE}检查Java版本...${NC}"
    java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1-2)

    if [[ "$java_version" != "1.8" ]]; then
        echo -e "${YELLOW}警告: 建议使用Java 8，当前版本: $java_version${NC}"
        echo "请使用Java 8以获得最佳性能分析效果:"
        echo "  export JAVA_HOME=/path/to/java8"
        echo "  export PATH=\$JAVA_HOME/bin:\$PATH"
    else
        echo -e "${GREEN}✓ Java 8已正确配置${NC}"
    fi
    echo
}

# 检查perf工具
check_perf_availability() {
    echo -e "${BLUE}检查perf工具可用性...${NC}"

    if command -v perf >/dev/null 2>&1; then
        perf_version=$(perf version 2>&1 | head -n1)
        echo -e "${GREEN}✓ perf工具可用: $perf_version${NC}"
        return 0
    else
        echo -e "${YELLOW}警告: perf工具未安装，将使用简化性能监控${NC}"
        echo "安装perf工具:"
        echo "  Ubuntu/Debian: sudo apt-get install linux-tools-common linux-tools-$(uname -r)"
        echo "  RHEL/CentOS: sudo yum install perf"
        return 1
    fi
    echo
}

# 编译Java代码
compile_java_code() {
    echo -e "${BLUE}编译Java 8压测代码...${NC}"

    if [ ! -f "examples/java8-perf-stress-example.java" ]; then
        echo -e "${RED}错误: 未找到Java源码文件 examples/java8-perf-stress-example.java${NC}"
        exit 1
    fi

    mkdir -p build/perf-stress

    javac -d build/perf-stress examples/java8-perf-stress-example.java

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Java编译成功${NC}"
    else
        echo -e "${RED}✗ Java编译失败${NC}"
        exit 1
    fi
    echo
}

# 创建输出目录
create_output_dirs() {
    echo -e "${BLUE}创建输出目录...${NC}"
    mkdir -p "$PERF_OUTPUT_DIR"
    mkdir -p "${PERF_OUTPUT_DIR}/cpu-stress"
    mkdir -p "${PERF_OUTPUT_DIR}/memory-stress"
    mkdir -p "${PERF_OUTPUT_DIR}/comprehensive-stress"
    echo -e "${GREEN}✓ 输出目录已创建${NC}"
    echo
}

# 运行CPU压力测试
run_cpu_stress_test() {
    echo -e "${YELLOW}=== 运行 CPU 压力测试 ===${NC}"

    local start_time=$(date +%s)

    if command -v perf >/dev/null 2>&1; then
        echo "使用perf进行CPU事件收集..."

        # 使用perf记录CPU性能事件
        perf stat -e cpu-clock,instructions,cache-misses,branch-misses \
            -o "${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-stat.txt" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 50 \
            --number 1000 \
            --max-tokens 256 \
            --output "${PERF_OUTPUT_DIR}/cpu-stress/" \
            --type cpu

        # perf record 记录调用图
        perf record -F 99 -g -o "${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-stress.data" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 100 \
            --number 500 \
            --output "${PERF_OUTPUT_DIR}/cpu-stress/"

        if [ $? -eq 0 ]; then
            # 生成CPU热点报告
            perf report -i "${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-stress.data" --stdio \
                > "${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-hotspots.txt" 2>/dev/null
        fi

    else
        echo "使用标准Java监控进行CPU测试..."

        java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 50 \
            --number 1000 \
            --max-tokens 256 \
            --output "${PERF_OUTPUT_DIR}/cpu-stress/" \
            --type cpu
    fi

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo -e "${GREEN}✓ CPU压测完成（耗时: ${duration}秒）${NC}"
    echo
}

# 运行内存压力测试
run_memory_stress_test() {
    echo -e "${YELLOW}=== 运行 内存 压力测试 ===${NC}"

    local start_time=$(date +%s)

    if command -v perf >/dev/null 2>&1; then
        echo "使用perf进行内存事件收集..."

        # 内存相关perf事件
        perf stat -e cache-misses,page-faults,minor-faults,major-faults,LLC-loads \
            -o "${PERF_OUTPUT_DIR}/memory-stress/perf-memory-stat.txt" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 30 \
            --number 500 \
            --max-tokens 2048 \
            --output "${PERF_OUTPUT_DIR}/memory-stress/" \
            --type memory

        # 记录内存分配模式
        perf record -F 99 -g -e cache-misses \
            -o "${PERF_OUTPUT_DIR}/memory-stress/perf-memory-cache.data" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 60 \
            --number 300 \
            --output "${PERF_OUTPUT_DIR}/memory-stress/"

    else
        # 使用JVM内存监控
        echo "使用JVM工具进行内存测试..."

        # 开启详细GC日志
        java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps \
            -Xloggc:"${PERF_OUTPUT_DIR}/memory-stress/gc.log" \
            -cp build/perf-stress Java8PerfStressExample \
            --concurrent 30 \
            --number 500 \
            --max-tokens 2048 \
            --output "${PERF_OUTPUT_DIR}/memory-stress/" \
            --type memory
    fi

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo -e "${GREEN}✓ 内存压测完成（耗时: ${duration}秒）${NC}"
    echo
}

# 运行综合压力测试
run_comprehensive_stress_test() {
    echo -e "${YELLOW}=== 运行 综合 压力测试 ===${NC}"

    local start_time=$(date +%s)

    echo "综合压测将混合CPU、内存、I/O负载..."

    if command -v perf >/dev/null 2>&1; then
        # 综合perf事件收集（火焰图准备）
        perf record -F 99 -g \
            -e cpu-clock,instructions,cache-misses,context-switches \
            -o "${PERF_OUTPUT_DIR}/comprehensive-stress/perf-comprehensive.data" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 100 \
            --number 1000 \
            --output "${PERF_OUTPUT_DIR}/comprehensive-stress/" \
            --type comprehensive

        # 生成综合统计
        perf stat -a --per-core \
            -e cpu-clock,task-clock,instructions,cache-misses,page-faults \
            -o "${PERF_OUTPUT_DIR}/comprehensive-stress/perf-comprehensive-stat.txt" \
            java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 80 \
            --number 800 \
            --output "${PERF_OUTPUT_DIR}/comprehensive-stress/"

        # 使用jcmd获取JVM内部信息
        local jvm_pid=$!
        if [ -n "$jvm_pid" ] && command -v jcmd >/dev/null 2>&1; then
            sleep 2
            jcmd "$jvm_pid" GC.class_histogram > "${PERF_OUTPUT_DIR}/comprehensive-stress/jvm-class-histogram.txt" 2>/dev/null || true
        fi

    else
        java -cp build/perf-stress Java8PerfStressExample \
            --concurrent 100 \
            --number 1000 \
            --output "${PERF_OUTPUT_DIR}/comprehensive-stress/" \
            --type comprehensive
    fi

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo -e "${GREEN}✓ 综合压测完成（耗时: ${duration}秒）${NC}"
    echo
}

# 生成火焰图（如果工具可用）
generate_flame_graph() {
    echo -e "${BLUE}=== 生成火焰图 ===${NC}"

    # 检查FlameGraph工具
    if [ -d "../FlameGraph" ] && [ -f "../FlameGraph/stackcollapse-perf.pl" ] && [ -f "../FlameGraph/flamegraph.pl" ]; then
        echo "使用FlameGraph工具生成火焰图..."

        perf script -i "${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-stress.data" | \
            ../FlameGraph/stackcollapse-perf.pl | \
            ../FlameGraph/flamegraph.pl \
            --title "EvalScope CPU Stress Test" \
            --width 1200 \
            > "${PERF_OUTPUT_DIR}/cpu-stress/flamegraph-cpu-stress.svg"

        echo -e "${GREEN}✓ 火焰图已生成: ${PERF_OUTPUT_DIR}/cpu-stress/flamegraph-cpu-stress.svg${NC}"

    else
        echo -e "${YELLOW}提示: FlameGraph工具未找到，跳过火焰图生成${NC}"
        echo "安装FlameGraph工具："
        echo "  git clone https://github.com/brendangregg/FlameGraph.git"
        echo "  python FlameGraph/stackcollapse-perf.pl --help"
    fi
    echo
}

# 分析压测结果
analyze_results() {
    echo -e "${BLUE}=== 分析压测结果 ===${NC}"

    echo "CPU压力测试结果："
    if [ -f "${PERF_OUTPUT_DIR}/cpu-stress/cpu_stress_results.json" ]; then
        grep -E '"(success_rate|avg_latency|total_requests)"' "${PERF_OUTPUT_DIR}/cpu-stress/cpu_stress_results.json" | head -10
    fi
    echo

    echo "内存压力测试结果："
    if [ -f "${PERF_OUTPUT_DIR}/memory-stress/memory_stress_results.json" ]; then
        grep -E '"(success_rate|memory_usage|gc_stats)"' "${PERF_OUTPUT_DIR}/memory-stress/memory_stress_results.json" | head -10
    fi
    echo

    echo "综合压力测试结果："
    if [ -f "${PERF_OUTPUT_DIR}/comprehensive-stress/comprehensive_stress_results.json" ]; then
        grep -E '"(success_rate|perf_statistics|memory_usage)"' "${PERF_OUTPUT_DIR}/comprehensive-stress/comprehensive_stress_results.json" | head -15
    fi
    echo
}

# 性能调优建议
generate_optimization_report() {
    echo -e "${BLUE}=== 性能调优建议报告 ===${NC}"

    local report_file="${PERF_OUTPUT_DIR}/java8-performance-optimization-report.md"

    cat > "$report_file" << 'EOF'
# EvalScope Java 8 性能压测优化报告

## 测试环境配置

### JVM参数建议
```bash
# CPU优化
export JAVA_OPTS="-Xms4g -Xmx8g \\
  -XX:+UseG1GC \\
  -XX:MaxGCPauseMillis=200 \\
  -XX:+UnlockDiagnosticVMOptions \\
  -XX:+DebugNonSafepoints \\
  -XX:+PreserveFramePointer"

# 内存优化
export JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=100 \\
  -XX:G1HeapRegionSize=16m \\
  -XX:G1NewSizePercent=30 \\
  -XX:G1MaxNewSizePercent=40"

# 线程优化
export JAVA_OPTS="$JAVA_OPTS -XX:ParallelGCThreads=8 \\
  -XX:ConcGCThreads=2"
```

### 系统级优化
```bash
# CPU频率锁定（性能模式）
echo performance | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# 内存相关
echo 1 | sudo tee /proc/sys/vm/drop_caches  # 清除缓存
ulimit -n 65536                           # 文件描述符限制

# perf权限设置
echo -1 | sudo tee /proc/sys/kernel/perf_event_paranoid
```

### 压测运行建议
```bash
# 基础压测
evalscope run --config examples/config.perf-stress.yaml --evaluation perf_cpu_stress

# 带性能分析的压测
perf stat -e cpu-clock,instructions,cache-misses -- \
  evalscope run --config examples/config.perf-stress.yaml

# 火焰图生成
perf record -F 99 -g -- evalscope run --config examples/config.perf-stress.yaml
perf script | ~/FlameGraph/stackcollapse-perf.pl | ~/FlameGraph/flamegraph.pl > flamegraph.svg

# JVM监控分析
jcmd $(pgrep java) VM.system_properties
jcmd $(pgrep java) GC.heap_info
jcmd $(pgrep java) VM.native_memory summary
```

## 性能指标解读

### CPU相关指标
- **CPU时钟周期**: 指令执行消耗的CPU时间
- **每指令周期数(CPI)**: < 1.0优秀，> 2.0需优化
- **缓存命中率**: > 95%良好，< 90%需关注
- **分支预测失败率**: < 10%可接受，> 15%需优化

### 内存相关指标
- **页面错误率**: 越低越好，区分轻微/严重页面错误
- **缓存未命中率**: < 5%良好，> 10%需优化
- **GC时间与频率**: GC时间 < 5%的应用运行时间

### 系统级指标
- **上下文切换率**: 避免过度切换
- **CPU迁移**: 减少跨CPU核心迁移
- **I/O等待**: < 20%最佳

## 调优验证
```bash
# 性能验证压测
java $JAVA_OPTS -cp build/perf-stress Java8PerfStressExample \
  --concurrent 100 \
  --number 2000 \
  --requests-per-second 50 \
  --output results/optimized/

# 对比基线性能
diff -u results/baseline-results.json results/optimized-results.json
```
EOF

    echo -e "${GREEN}✓ 优化报告已生成: $report_file${NC}"
    echo
}

# 主流程
main() {
    echo -e "${BLUE}开始EvalScope Java 8 Perf压测流程...${NC}"

    check_java_version
    check_perf_availability

    if [ $? -eq 0 ]; then
        echo "将使用perf工具进行系统级性能分析"
    else
        echo "将使用JVM内置监控进行性能分析"
    fi

    compile_java_code
    create_output_dirs

    # 运行不同模式的压测
    run_cpu_stress_test
    run_memory_stress_test
    run_comprehensive_stress_test

    # 生成分析结果
    generate_flame_graph
    analyze_results
    generate_optimization_report

    # 显示结果概览
    echo -e "${GREEN}================================================${NC}"
    echo -e "${GREEN}✓ Java 8 Perf压测流程完成！${NC}"
    echo -e "${BLUE}结果目录: $PERF_OUTPUT_DIR${NC}"
    echo -e "${BLUE}详细报告: ${PERF_OUTPUT_DIR}/java8-performance-optimization-report.md${NC}"
    echo
    echo "后续分析命令："
    echo "  # 查看perf统计"
    echo "  cat ${PERF_OUTPUT_DIR}/cpu-stress/perf-cpu-stat.txt"
    echo
    echo "  # 查看火焰图"
    echo "  open ${PERF_OUTPUT_DIR}/cpu-stress/flamegraph-cpu-stress.svg"
    echo
    echo "  # 查看JVM GC日志"
    echo "  cat ${PERF_OUTPUT_DIR}/memory-stress/gc.log"
}

# 处理命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-compilation)
            SKIP_COMPILATION=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --skip-compilation    跳过Java编译步骤"
            echo "  --help                显示帮助信息"
            exit 0
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

# 如果没有跳过编译，运行完整流程
if [ "$SKIP_COMPILATION" != true ]; then
    main
else
    # 跳过编译，直接运行测试
    echo -e "${BLUE}跳过编译步骤，直接运行压测...${NC}"
    create_output_dirs
    run_cpu_stress_test
    run_memory_stress_test
    run_comprehensive_stress_test
fi