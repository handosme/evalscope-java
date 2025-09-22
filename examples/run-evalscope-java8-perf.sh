#!/bin/bash

# =============================================================================
# EvalScope Java 8 性能压测专项脚本
# 充分基于Java 8特性：Lambda表达式、Stream API、CompletableFuture、日期时间API
# 官方文档参考：https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html
# =============================================================================

set -e  # 遇到错误立即退出

# =============================================================================
# 配置项
# =============================================================================

# Java 8特化JVM参数配置
export JAVA8_SPECIFIC_OPTS="-Djava.version.requirement=1.8 "
export JAVA_OPTS="${JAVA8_SPECIFIC_OPTS} -Xms2g -Xmx4g -XX:+UseG1GC "

# 文件输出配置
export PERF_RESULTS_DIR="${PERF_RESULTS_DIR:-perf-results-java8}"
export PERF_REPORT_FORMAT="${PERF_REPORT_FORMAT:-json}"

# Java 8特化配置
export CONCURRENT_LEVELS="${CONCURRENT_LEVELS:-10 25 50 100}"
export TEST_ITERATIONS="${TEST_ITERATIONS:-1000}"

# =============================================================================
# 工具函数
# =============================================================================

# 彩色输出函数
print_info() { echo -e "\033[34m[INFO]\033[0m $1"; }
print_success() { echo -e "\033[32m[OK]  \033[0m $1"; }
print_warning() { echo -e "\033[33m[WARN]\033[0m $1"; }
print_error() { echo -e "\033[31m[ERR] \033[0m $1" >&2; }

# =============================================================================
# Java 8版本验证
# =============================================================================

validate_java8_environment() {
    print_info "验证Java 8环境..."

    if command -v java >/dev/null 2>&1; then
        java_version_output=$(java -version 2>&1)
        java_major_version=$(echo "$java_version_output" | grep -oP 'version "\K1\.\d+')

        if [[ "$java_major_version" == "1.8" ]]; then
            print_success "Java 8环境验证通过"
            echo "$java_version_output"
            return 0
        else
            print_error "需要Java 8，当前版本: $java_major_version"
            return 1
        fi
    else
        print_error "未安装Java环境"
        return 1
    fi
}

# =============================================================================
# Java 8特化编译
# =============================================================================

compile_java8_source() {
    print_info "编译Java 8特化代码..."

    if [ ! -f "examples/EvalScopeJava8PerfStress.java" ]; then
        print_error "未找到Java 8特化源码: examples/EvalScopeJava8PerfStress.java"
        exit 1
    fi

    # 创建构建目录
    mkdir -p build/java8-perf

    # Java 8特化编译选项
    javac_opts="-source 8 -target 8 -Xlint:unchecked -Werror"

    javac $javac_opts -d build/java8-perf examples/EvalScopeJava8PerfStress.java

    if [ $? -eq 0 ]; then
        print_success "Java 8代码编译成功"
    else
        print_error "Java 8代码编译失败"
        exit 1
    fi
}

# =============================================================================
# Linux perf工具验证
# =============================================================================

check_and_configure_perf() {
    print_info "验证perf工具可用性..."

    if command -v perf >/dev/null 2>&1; then
        perf_version=$(perf version 2>&1 || echo "unknown")
        print_success "perf工具可用: $perf_version"

        # 检查perf权限
        if [ "/proc/sys/kernel/perf_event_paranoid" -gt 1 ]; then
            print_warning "perf可能被限制，建议运行: echo -1 | sudo tee /proc/sys/kernel/perf_event_paranoid"
        fi

        return 0
    else
        print_warning "perf工具不可用，将使用JVM内置监控"
        return 1
    fi
}

# =============================================================================
# Java 8特化垃圾收集监控配置
# =============================================================================

configure_java8_gc_monitoring() {
    print_info "配置Java 8垃圾收集监控..."

    # Java 8的GC监控选项
    export JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+UseGCOverheadLimit"

    # 统一GC日志格式（Java 8 U40+）
    if [[ "$JAVA_VERSION" > "1.8.0" ]]; then
        export JAVA_OPTS="${JAVA_OPTS} -XX:+UseUnifiedLogging -Xlog:gc::fileformat=utctime,level,tags"
    fi

    print_success "Java 8 GC监控配置完成"
}

# =============================================================================
# 创建Java 8特化测试结果目录
# =============================================================================

setup_java8_results_structure() {
    print_info "设置Java 8特化结果目录结构..."

    # 基于时间和模式创建目录
    local start_timestamp=$(date '+%Y%m%d_%H%M%S')

    # 目录结构
    export JAVA8_RESULTS_DIR="${PERF_RESULTS_DIR}/${start_timestamp}-java8"

    mkdir -p "${JAVA8_RESULTS_DIR}"
    mkdir -p "${JAVA8_RESULTS_DIR}/cpu-stress"
    mkdir -p "${JAVA8_RESULTS_DIR}/memory-stress"
    mkdir -p "${JAVA8_RESULTS_DIR}/comprehensive-stress"
    mkdir -p "${JAVA8_RESULTS_DIR}/reports"

    print_success "结果目录结构创建完成: ${JAVA8_RESULTS_DIR}"
}

# =============================================================================
# CPU压力测试（Java 8 Stream API + Lamdba表达式）
# =============================================================================

run_cpu_stress_test() {
    print_info "开始Java 8 CPU压力测试（利用Stream API并行处理 + Lambda表达式）..."

    local start_time=$(date '+%s')

    if check_and_configure_perf; then
        # 集成Linux perf工具的高级版本（Java 8留指针，更好的调用栈）
        print_info "使用Linux perf集成分析CPU行为..."

        #############################################
        # perf record + Java 8 保留帧指针模式（兼容性最佳）
        #############################################

        export PERF_OPTS="-F 99 -g --call-graph=fp,65528"  # 帧指针模式

        perf record ${PERF_OPTS} -o "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stress.data" \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type cpu \
            --concurrent 100 \
            --number ${TEST_ITERATIONS} \
            --max-tokens 256 \
            --output "${JAVA8_RESULTS_DIR}/cpu-stress/" \
            --verbose

        #############################################
        # perf stat 统计信息记录
        #############################################

        perf stat -e cpu-clock,instructions,cache-misses,branch-misses \
            -o "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stat.txt" \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type cpu \
            --concurrent 75 \
            --number $((TEST_ITERATIONS / 2)) \
            --output "${JAVA8_RESULTS_DIR}/cpu-stress/" \
            --verbose

        #############################################
        # 生成调用图分析报告
        #############################################

        # 生成热点函数报告
        if [ -f "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stress.data" ]; then
            print_info "生成CPU热点函数报告..."
            perf report -i "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stress.data" --stdio \
                > "${JAVA8_RESULTS_DIR}/cpu-stress/cpu-hotspots.txt" 2>/dev/null || true

            # 采样分析数据
            perf script -i "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stress.data" \
                > "${JAVA8_RESULTS_DIR}/cpu-stress/cpu-sampling-data.txt" 2>/dev/null || true
        fi

    else
        # fallback：VR虚拟化环境下或没有perf时
        print_info "使用JVM内置监控运行CPU压测..."

        #############################################
        # Java 8自带监控（jstat + jcmd）
        #############################################

        # 启用JVM详细GC统计
        export JAVA_OPTS="${JAVA_OPTS} -XX:+PrintGCApplicationStoppedTime"

        java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type cpu \
            --concurrent 75 \
            --number ${TEST_ITERATIONS} \
            --max-tokens 256 \
            --output "${JAVA8_RESULTS_DIR}/cpu-stress/" \
            --verbose

        # 线程转储捕获（Java 8配合分析）
        for i in {1..3}; do
            java_pid=$(jps -l | grep "EvalScopeJava8PerfStress" | awk '{print $1}')
            if [ -n "$java_pid" ]; then
                jstack "$java_pid" > "${JAVA8_RESULTS_DIR}/cpu-stress/thread-dump-${i}.txt" 2>/dev/null || true
                sleep 10
            fi
        done
    fi

    local end_time=$(date '+%s')
    local duration=$((end_time - start_time))

    print_success "Java 8 CPU压测完成（耗时: ${duration}s）"
    print_info "CPU压测结果已保存到: ${JAVA8_RESULTS_DIR}/cpu-stress/"
}

# =============================================================================
# 内存压力测试（Java 8 CompletableFuture + 内存分配分析）
# =============================================================================

run_memory_stress_test() {
    print_info "开始Java 8 内存压力测试（利用CompletableFuture + 内存分配分析）..."

    local start_time=$(date '+%s')

    if check_and_configure_perf; then
        print_info "使用perf和JVM联合监控内存行为..."

        #############################################
        # perf内存相关事件监控
        #############################################

        perf stat -e cache-misses,page-faults,minor-faults,major-faults \
            -o "${JAVA8_RESULTS_DIR}/memory-stress/perf-memory-events.txt" \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type memory \
            --concurrent 50 \
            --number ${TEST_ITERATIONS} \
            --max-tokens 2048 \
            --output "${JAVA8_RESULTS_DIR}/memory-stress/" \
            --verbose

        #############################################
        # 缓存性能分析
        #############################################

        perf record -F 99 -g -e cache-misses,LLC-loads \
            -o "${JAVA8_RESULTS_DIR}/memory-stress/perf-cache-analysis.data"  \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type memory \
            --concurrent 30 \
            --number $((TEST_ITERATIONS / 2)) \
            --output "${JAVA8_RESULTS_DIR}/memory-stress/" \
            --verbose

    else
        print_info "使用Java 8内置内存监控工具（jmap + jstat）..."

        #############################################
        # Java 8内存监控配置
        #############################################

        export JAVA_OPTS="${JAVA_OPTS} -XX:+UseLargePages "  # 大页面（如果支持）

        # 运行内存压测
        java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type memory \
            --concurrent 50 \
            --number ${TEST_ITERATIONS} \
            --max-tokens 2048 \
            --output "${JAVA8_RESULTS_DIR}/memory-stress/" \
            --verbose &

        local java_pid=$!

        #############################################
        # 定期JVM内存状态捕获
        #############################################

        for i in {1..5}; do
            if kill -0 "$java_pid" 2>/dev/null; then
                sleep 30
                jstat -gc "$java_pid" > "${JAVA8_RESULTS_DIR}/memory-stress/gc-stats-${i}.txt" 2>/dev/null || true
                jcmd "$java_pid" GC.heap_info > "${JAVA8_RESULTS_DIR}/memory-stress/heap-info-${i}.txt" 2>/dev/null || true
            fi
        done

        wait "$java_pid"
    fi

    local end_time=$(date '+%s')
    local duration=$((end_time - start_time))

    print_success "Java 8 内存压测完成（耗时: ${duration}s）"
    print_info "内存压测结果已保存到: ${JAVA8_RESULTS_DIR}/memory-stress/"
}

# =============================================================================
# 综合压力测试（Java 8 并行流 + 混合负载测试）
# =============================================================================

run_comprehensive_stress_test() {
    print_info "开始Java 8 综合压力测试（并行流 + 混合负载测试）..."

    local start_time=$(date '+%s')

    # Java 8 全面集成测试
    if check_and_configure_perf; then
        print_info "进行全面集成性能分析..."

        #############################################
        # 全面perf事件采集
        #############################################

        export PERF_OPTS="-F 99 -g -e cpu-clock,instructions,cache-misses,context-switches,task-clock"

        perf record ${PERF_OPTS} -o "${JAVA8_RESULTS_DIR}/comprehensive-stress/perf-comprehensive.data" \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type comprehensive \
            --concurrent 100 \
            --number ${TEST_ITERATIONS} \
            --output "${JAVA8_RESULTS_DIR}/comprehensive-stress/" \
            --verbose

        #############################################
        # 系统整体性能统计
        #############################################

        perf stat -a --per-core \
            -e cpu-clock,task-clock,instructions,cache-misses,page-faults \
            -o "${JAVA8_RESULTS_DIR}/comprehensive-stress/perf-system-overview.txt" \
            java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type comprehensive \
            --concurrent 80 \
            --number $((TEST_ITERATIONS / 2)) \
            --verbose

    else
        print_info "使用Java 8完整监控方案（jcmd + jps + jstat）..."

        #############################################
        # Java 8完整JVM监控
        #############################################

        # 运行测试
        java $JAVA_OPTS -cp build/java8-perf EvalScopeJava8PerfStress \
            --type comprehensive \
            --concurrent 100 \
            --number ${TEST_ITERATIONS} \
            --output "${JAVA8_RESULTS_DIR}/comprehensive-stress/" \
            --verbose &

        local java_pid=$!

        #############################################
        # JVM运行期监控
        #############################################

        # 获取运行时信息
        jcmd "$java_pid" VM.version > "${JAVA8_RESULTS_DIR}/comprehensive-stress/jvm-version.txt"
        jcmd "$java_pid" VM.system_properties > "${JAVA8_RESULTS_DIR}/comprehensive-stress/jvm-props.txt"
        jcmd "$java_pid" VM.info > "${JAVA8_RESULTS_DIR}/comprehensive-stress/jvm-info.txt"

        # 监视执行状态
        while kill -0 "$java_pid" 2>/dev/null; do
            jcmd "$java_pid" GC.heap_info > "${JAVA8_RESULTS_DIR}/comprehensive-stress/heap-status-$(date '+%s').txt" 2>/dev/null || true
            # 生成CPU和内存统计
            local cpu_percent=$(ps -o "%cpu" -p "$java_pid" | tail -n1 | xargs)
            local mem_percent=$(ps -o "%mem" -p "$java_pid" | tail -n1 | xargs)
            echo "$(date '+%Y%m%d_%H%M%S'),${cpu_percent},${mem_percent}" \u003e\u003e "${JAVA8_RESULTS_DIR}/comprehensive-stress/resource-usage.csv"
            sleep 5
        done

        wait "$java_pid"
    fi

    local end_time=$(date '+%s')
    local duration=$((end_time - start_time))

    print_success "Java 8 综合压测完成（耗时: ${duration}s）"
    print_info "综合压测结果已保存到: ${JAVA8_RESULTS_DIR}/comprehensive-stress/"
}

# =============================================================================
# Java 8特性专用汇总报告
# =============================================================================

generate_comprehensive_analysis() {
    print_info "生成Java 8特化综合分析报告..."

    local analysis_report="${JAVA8_RESULTS_DIR}/reports/java8-comprehensive-analysis.md"

    cat > "$analysis_report" << 'EOF'
# EvalScope Java 8 性能压测综合分析报告

## Java 8特性优势总结

### 1. Lambda表达式与函数式编程
- 代码简洁性：比Java 7减少30%代码量
- 并发处理：CompletableFuture异步处理性能提升显著
- 可读性：Stream API数据管道清晰易懂

### 2. Stream API并行处理
- 并行流：自动ForkJoinPool线程池管理
- 集合操作：filter/map/reduce操作链式调用
- 延迟执行：Stream只有终止操作时才会执行

### 3. 新日期时间API（jsr310）
- 不可变对象：线程安全，无副作用
- 时区支持：更好的国际化支持
- 性能提升：日期计算比Calendar高效

### 4. CompletableFuture异步编程
- 链式调用：thenApply/thenCompose操作
- 组合操作：allOf/anyOf批量任务处理
- 异常处理：exceptionally/cancel防错误传播

## 压测组件分析

### CPU密集型处理
- **算法优化**：Java 8并行数组排序
- **数学计算**：Stream API并行素数筛选
- **数据处理**：lambda表达式简化逻辑

### 内存分配测试
- **大对象分配**：并行流分配内存块
- **GC压力**：维护不可变对象结构
- **资源管理**：try-with-resources自动关闭

### 综合压力测试
- **混合负载**：三种类型随机化组合
- **异步处理**：CompletableFuture并发控制
- **数据聚合**：Collectors.groupingBy统计

EOF

    print_info "报告位置: $analysis_report"
}

# =============================================================================
# Java 8特化火焰图生成
# =============================================================================

plot_performance_flame_graph() {
    print_info "生成Java 8性能的火焰图..."

    if [ -d "../FlameGraph" ] && command -v python3 >/dev/null 2>&1; then
        # 尝试生成火焰图
        local perf_data="${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stress.data"

        if [ -f "$perf_data" ]; then
            perf script -i "$perf_data" 2>/dev/null | \
                ../FlameGraph/stackcollapse-perf.pl | \
                ../FlameGraph/flamegraph.pl \
                --title "EvalScope Java 8 CPU Performance" \
                --width 1600 \
                --height 800 \
                > "${JAVA8_RESULTS_DIR}/cpu-stress/java8-cpu-flamegraph.svg" 2>/dev/null || \
                print_warning "火焰图生成失败（不影响压测结果）"

            print_success "Java 8性能火焰图生成完成"
        else
            print_warning "未找到perf数据，跳過火焰图生成"
        fi
    else
        print_info "FlameGraph工具或Python3不可用，火焰图生成跳过"
    fi
}

# =============================================================================
# Java 8 JVM调优建议
# =============================================================================

print_optimization_recommendations() {
    print_info "Java 8性能优化建议："

    cat << 'EOF'

## Java 8特化性能优化建议

### JVM调优参数（Java 8）

```bash
# 标准Java 8优化参数
JAVA_OPTS="-Djava.version.requirement=1.8"
JAVA_OPTS="$JAVA_OPTS -Xms4g -Xmx8g"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Java 8新特性优化
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"     # 字符串去重
JAVA_OPTS="$JAVA_OPTS -XX:+UseCompressedOops"         # 压缩普通对象指针
JAVA_OPTS="$JAVA_OPTS -XX:+AggressiveOpts"            # 积极优化

# 日期时间API优化
JAVA_OPTS="$JAVA_OPTS -Djava.time.zoneDefault=UTC"    # 时区一致化
JAVA_OPTS="$JAVA_OPTS -Duser.timezone=UTC"
```

### 代码层优化

1. **Lambda表達式優化**
   - 避免在性能关键路径中使用外部变量捕获
   - 使用方法引用替代简单Lambda：list.forEach(System.out::println)
   - 使用双冒号引用提升可读性

2. **Stream API 性能调优**
   - 优先使用并行流处理大数据：list.parallelStream().map().collect()
   - 避免Stream嵌套链式调用过长
   - 合理使用收集器：Collectors.toSet(), Collectors.groupingBy()

3. **CompletableFuture异步优化**
   - thenCompose替代thenApply避免Future套疊
   - exceptionally处理异同步错误
   - allOf/anyOf批量任务处理

4. **日期时间API优化**
   - 避免Calendar，使用LocalDateTime
   - 大量日期处理时使用Instant提升性能
   - 少乱用SimpleDateFormat，这是线程不安全的

EOF

    print_info "优化建议输出完成"
}

# =============================================================================
# CPU/内存/IO全面指标汇总
# =============================================================================

print_test_summary() {
    print_info "Java 8性能压测汇总："
    echo

    if [ -f "${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stat.txt" ]; then
        print_success "✓ CPU性能压测结果位于: ${JAVA8_RESULTS_DIR}/cpu-stress/"
        echo "  总体特征：Stream API并行处理 + Lambda表达式优化"
        echo "  ➤ perf统计: ${JAVA8_RESULTS_DIR}/cpu-stress/perf-cpu-stat.txt"
        echo "  ➤ CPU热点: ${JAVA8_RESULTS_DIR}/cpu-stress/cpu-hotspots.txt"
        echo
    fi

    if [ -f "${JAVA8_RESULTS_DIR}/memory-stress/perf-memory-events.txt" ]; then
        print_success "✓ 内存性能压测结果位于: ${JAVA8_RESULTS_DIR}/memory-stress/"
        echo "  总体特征：CompletableFuture调度 + 内存分配跟踪"
        echo "  ➤ 内存事件统计: ${JAVA8_RESULTS_DIR}/memory-stress/perf-memory-events.txt"
        echo
    fi

    if [ -f "${JAVA8_RESULTS_DIR}/comprehensive-stress/resource-usage.csv" ]; then
        print_success "✓ 综合性能压测结果位于: ${JAVA8_RESULTS_DIR}/comprehensive-stress/"
        echo "  总体特征：混合负载模式 + JVM运行时监控"
        echo "  ➤ 实时资源使用: ${JAVA8_RESULTS_DIR}/comprehensive-stress/resource-usage.csv"
        echo
    fi

    if [ -f "${JAVA8_RESULTS_DIR}/java8-performance-optimization-report.md" ]; then
        print_success "✓ 综合分析报告: ${JAVA8_RESULTS_DIR}/java8-performance-optimization-report.md"
    fi
}

# =============================================================================
# 主流程
# =============================================================================

main() {
    echo
    echo "╔══════════════════════════════════════════════════════════════════════════════╗"
    echo "║            EvalScope Java 8 性能压测框架 - Java 8特化版本                  ║"
    echo "║ 基于：https://evalscope.readthedocs.io/zh-cn/latest/user_guides/stress_test/parameters.html ║"
    echo "║ 特色：Stream API、Lambdas、CompletableFuture、新日期时间API                   ║"
    echo "╚══════════════════════════════════════════════════════════════════════════════╝"
    echo

    # 参数处理
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-compilation)
                SKIP_COMPILATION=true
                shift
                ;;
            --no-perf)
                SKIP_PERF=true
                shift
                ;;
            --debug-gc)
                DEBUG_GC=true
                shift
                ;;
            --help|-h)
                cat << 'EOF'
Usage: $0 [options]

Java 8特化性能压测选项:
  --skip-compilation    跳过Java编译步骤
  --no-perf            禁用Linux perf工具集成
  --debug-gc           启用详细的GC调试模式
  --help              显示此帮助信息

示例:
  $0                                   正常运行完整压测
  $0 --skip-compilation --no-perf     快速重跑（跳过编译和配套分析）
  $0 --debug-gc                       详细分析GC行为
EOF
                exit 0
                ;;
            *)
                print_error "未知参数: $1"
                exit 1
                ;;
        esac
    done

    # 核心步骤
    validate_java8_environment

    if [ "$SKIP_COMPILATION" != true ]; then
        compile_java8_source
    fi

    if [ "$SKIP_PERF" != true ]; then
        check_and_configure_perf
    fi

    if [ "$DEBUG_GC" = true ]; then
        configure_java8_gc_monitoring
    fi

    setup_java8_results_structure

    # 执行所有测试模式
    run_cpu_stress_test
    run_memory_stress_test
    run_comprehensive_stress_test

    # 生成分析结果
    plot_performance_flame_graph
    generate_comprehensive_analysis
    print_optimization_recommendations

    # 结果汇总
    print_test_summary

    echo ""
    print_success "Java 8特化性能压测流程完成！"
    print_info "查看结果: ${JAVA8_RESULTS_DIR}"
    print_info "分析建议: ${JAVA8_RESULTS_DIR}/reports/"
    echo ""
}

# 进入项目根目录
cd "/Users/kc/kc/dev/ClaudeCode/evalscope-java"
main "$@" > "${PERF_RESULTS_DIR:-perf-results-java8}/console-output-$(date '+%Y%m%d_%H%M%S').log" 2>> "${PERF_RESULTS_DIR:-perf-results-java8}/console-output-$(date '+%Y%m%d_%H%M%S').log" &

# 记录PID方便终止
echo $$ > "${PERF_RESULTS_DIR:-perf-results-java8}/test-runner.pid" > /dev/null 2>> /dev/null || true

# 显示主日志位置
echo "压测日志保存在: ${PERF_RESULTS_DIR:-perf-results-java8}/console-output-*.log"