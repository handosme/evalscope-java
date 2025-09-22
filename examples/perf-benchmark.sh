#!/bin/bash

# EvalScope perf 性能分析测试脚本
# 使用Linux perf工具进行系统级性能分析

echo "=== EvalScope Perf 性能分析测试 ==="
echo "测试时间: $(date)"
echo "PID: $$"
echo

# 设置颜色和输出格式
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查perf是否安装
if ! command -v perf &> /dev/null; then
    echo -e "${RED}错误: perf 工具未安装${NC}"
    echo "请安装perf:"
    echo "  Ubuntu/Debian: sudo apt-get install linux-tools-common linux-tools-$(uname -r)"
    echo "  CentOS/RHEL: sudo yum install perf"
    echo "  Fedora: sudo dnf install perf"
    exit 1
fi

# 检查是否在Linux系统上
if [[ "$OSTYPE" != "linux-gnu"* ]]; then
    echo -e "${RED}错误: perf 工具仅在Linux系统上可用${NC}"
    exit 1
fi

# 检查权限
if [[ $EUID -ne 0 ]]; then
    echo -e "${YELLOW}警告: 非root用户运行，某些perf功能可能受限${NC}"
    echo "建议以root用户运行以获得完整功能"
    echo
fi

# 测试结果目录
RESULTS_DIR="perf-results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TEST_DIR="$RESULTS_DIR/perf_test_$TIMESTAMP"

# 创建结果目录
mkdir -p "$TEST_DIR"

# 检查JAR文件
if [ ! -f "target/evalscope-*.jar" ]; then
    echo -e "${RED}错误: 未找到目标 JAR 文件${NC}"
    echo "请先运行: mvn clean package"
    exit 1
fi

JAR_FILE=$(ls target/evalscope-*.jar | head -n 1)
echo -e "${BLUE}使用 JAR 文件: $JAR_FILE${NC}"
echo

# 测试配置
PERF_DURATION=${PERF_DURATION:-60}  # perf采样时长(秒)
WARMUP_TIME=${WARMUP_TIME:-10}     # 预热时间(秒)
CONCURRENT_LEVELS=${CONCURRENT_LEVELS:-"1 5 10 20"}
TEST_ITERATIONS=${TEST_ITERATIONS:-100}

# 函数: 运行带perf的EvalScope测试
run_perf_test() {
    local test_name="$1"
    local concurrent="$2"
    local perf_record_opts="$3"
    local extra_opts="$4"

    echo -e "${YELLOW}=== 运行测试: $test_name (并发: $concurrent) ===${NC}"

    local perf_data_file="$TEST_DIR/perf_${test_name}_c${concurrent}.data"
    local perf_report_file="$TEST_DIR/perf_${test_name}_c${concurrent}.txt"
    local evalscope_output="$TEST_DIR/evalscope_${test_name}_c${concurrent}.json"

    # 构建EvalScope命令
    local evalscope_cmd="java -jar $JAR_FILE \
        --config examples/perf-benchmark-config.yaml \
        --evaluation-type perf_stress \
        --concurrent $concurrent \
        --number $TEST_ITERATIONS \
        --max-workers $((concurrent * 2)) \
        --output $evalscope_output \
        --debug"

    echo -e "${BLUE}EvalScope命令: $evalscope_cmd${NC}"
    echo -e "${BLUE}Perf命令: perf record $perf_record_opts -o $perf_data_file${NC}"
    echo

    # 预热阶段
    echo -e "${YELLOW}预热阶段 ($WARMUP_TIME 秒)...${NC}"
    timeout $WARMUP_TIME java -jar "$JAR_FILE" --dry-run 2>/dev/null || true

    # 同时启动perf和EvalScope
    echo -e "${YELLOW}开始性能采样 ($PERF_DURATION 秒)...${NC}"

    # 使用perf记录系统性能数据
    perf record $perf_record_opts -o "$perf_data_file" -- \
        timeout $PERF_DURATION $evalscope_cmd 2>&1 | tee "$TEST_DIR/evalscope_${test_name}_c${concurrent}.log"

    local exit_code=$?

    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}✓ 测试完成${NC}"

        # 生成perf报告
        echo -e "${YELLOW}生成perf报告...${NC}"
        perf report -i "$perf_data_file" --stdio > "$perf_report_file" 2>/dev/null

        # 生成火焰图(如果可用)
        if command -v perf-script &> /dev/null; then
            echo -e "${YELLOW}生成火焰图数据...${NC}"
            perf script -i "$perf_data_file" > "$TEST_DIR/perf_${test_name}_c${concurrent}.stacks" 2>/dev/null
        fi

        return 0
    else
        echo -e "${RED}✗ 测试失败 (exit code: $exit_code)${NC}"
        return 1
    fi
}

# 函数: 分析CPU性能
analyze_cpu_performance() {
    echo -e "${YELLOW}=== CPU性能分析 ===${NC}"

    # CPU热点分析
    for data_file in "$TEST_DIR"/perf_*_c*.data; do
        if [ -f "$data_file" ]; then
            local base_name=$(basename "$data_file" .data)
            echo -e "${BLUE}分析: $base_name${NC}"

            # Top函数分析
            perf report -i "$data_file" --stdio --sort comm,dso | head -20 > "$TEST_DIR/${base_name}_top_functions.txt"

            # CPU周期分析
            perf report -i "$data_file" --stdio --sort cpu | head -10 > "$TEST_DIR/${base_name}_cpu_cycles.txt"

            echo -e "${GREEN}✓ CPU分析报告已生成${NC}"
        fi
    done
    echo
}

# 函数: 分析内存性能
analyze_memory_performance() {
    echo -e "${YELLOW}=== 内存性能分析 ===${NC}"

    # 运行专门的内mem性能测试
    echo -e "${BLUE}运行内存分配分析...${NC}"

    # 使用perf记录内存分配事件
    perf stat -e cache-misses,cache-references,page-faults \
        java -jar "$JAR_FILE" \
        --config examples/perf-benchmark-config.yaml \
        --evaluation-type memory_test \
        --concurrent 5 \
        --number 50 \
        --output "$TEST_DIR/memory_test_results.json" 2>&1 | tee "$TEST_DIR/memory_perf_stat.txt"

    echo -e "${GREEN}✓ 内存性能数据已收集${NC}"
    echo
}

# 函数: 生成综合性能报告
generate_performance_report() {
    echo -e "${YELLOW}=== 生成综合性能报告 ===${NC}"

    local report_file="$TEST_DIR/performance_report_$TIMESTAMP.md"

    cat > "$report_file" << EOF
# EvalScope Perf性能分析报告

**测试时间**: $(date)
**测试目录**: $TEST_DIR
**测试配置**: perf-benchmark-config.yaml

## 测试环境

- **操作系统**: $(uname -a)
- **CPU信息**: $(grep "model name" /proc/cpuinfo | head -1 | cut -d: -f2)
- **内存信息**: $(free -h | grep "Mem:")
- **Java版本**: $(java -version 2>&1 | head -1)
- **Perf版本**: $(perf version 2>&1)

## 测试配置

- **采样时长**: ${PERF_DURATION}秒
- **预热时间**: ${WARMUP_TIME}秒
- **并发级别**: $CONCURRENT_LEVELS
- **测试迭代**: $TEST_ITERATIONS

## CPU性能分析

### 热点函数分析

\`\`\`
$(ls "$TEST_DIR"/*_top_functions.txt 2>/dev/null | head -1 | xargs cat 2>/dev/null || echo "无数据")
\`\`\`

### CPU周期分布

\`\`\`
$(ls "$TEST_DIR"/*_cpu_cycles.txt 2>/dev/null | head -1 | xargs cat 2>/dev/null || echo "无数据")
\`\`\`

## 内存性能指标

\`\`\`
$(cat "$TEST_DIR/memory_perf_stat.txt" 2>/dev/null | grep -E "(cache-misses|cache-references|page-faults)" || echo "无数据")
\`\`\`

## 性能调优建议

### CPU优化
1. 监控CPU密集型函数，考虑算法优化
2. 关注缓存命中率，优化数据访问模式
3. 分析线程竞争情况，优化并发策略

### 内存优化
1. 减少页面错误，优化内存分配
2. 提高缓存命中率，优化数据结构
3. 监控GC活动，调整JVM参数

### 系统级优化
1. 考虑CPU亲和性设置
2. 优化线程调度策略
3. 监控I/O等待时间

## 测试输出文件

$(ls -la "$TEST_DIR" | grep -E "\.(data|txt|json|log)$" | awk '{print "- " $9 " (" $5 " bytes)"}')

---
*报告生成时间: $(date)*
EOF

    echo -e "${GREEN}✓ 性能报告已生成: $report_file${NC}"
}

# 函数: 性能调优建议
performance_tuning_tips() {
    echo -e "${BLUE}=== 性能调优建议 ===${NC}"
    echo

    cat << 'EOF'
## JVM调优建议

export JAVA_OPTS="
-Xms2g -Xmx4g              # 合理设置堆内存
-XX:+UseG1GC               # 使用G1垃圾收集器
-XX:MaxGCPauseMillis=200   # GC暂停时间目标
-XX:+PrintGCDetails        # GC日志
-XX:+UseStringDeduplication # 字符串去重
-XX:+OptimizeStringConcat  # 字符串拼接优化
"

## 系统级调优

# CPU亲和性
taskset -c 0-3 java -jar app.jar

# 提高文件描述符限制
ulimit -n 65536

# JVM性能监控
-XX:+UnlockDiagnosticVMOptions
-XX:+DebugNonSafepoints
-XX:+PreserveFramePointer   # 更准确的perf调用栈

EOF
    echo
}

# 主测试流程
echo -e "${BLUE}开始perf性能分析测试...${NC}"
echo

# 1. CPU性能分析测试
for concurrent in $CONCURRENT_LEVELS; do
    run_perf_test "cpu_analysis" "$concurrent" "-e cpu-clock,cache-misses,cache-references" "CPU性能分析"
done

# 2. 内存性能分析
analyze_memory_performance

# 3. CPU性能详细分析
analyze_cpu_performance

# 4. 生成性能报告
generate_performance_report

# 5. 显示调优建议
performance_tuning_tips

# 显示测试总结
echo -e "${GREEN}=== Perf性能分析测试完成 ===${NC}"
echo -e "${BLUE}测试结果保存在: $TEST_DIR${NC}"
echo -e "${BLUE}性能报告: $TEST_DIR/performance_report_$TIMESTAMP.md${NC}"
echo

# 显示后续分析命令
echo -e "${YELLOW}后续分析命令建议:${NC}"
echo "# 查看perf报告"
echo "perf report -i $TEST_DIR/perf_cpu_analysis_c10.data"
echo
echo "# 生成火焰图(需要FlameGraph工具)"
echo "perf script -i $TEST_DIR/perf_cpu_analysis_c10.data | ~/FlameGraph/stackcollapse-perf.pl | ~/FlameGraph/flamegraph.pl > $TEST_DIR/flamegraph.svg"
echo
echo "# 统计信息"
echo "perf stat -i $TEST_DIR/perf_cpu_analysis_c10.data"