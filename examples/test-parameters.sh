#!/bin/bash

# EvalScope 参数功能测试脚本
# 本脚本测试 EvalScope 的所有命令行参数功能

echo "=== EvalScope 参数功能测试 ==="
echo "测试时间: $(date)"
echo

# 设置颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# 测试结果统计
PASSED=0
FAILED=0
SKIPPED=0

# 测试函数
test_parameter() {
    local test_name="$1"
    local command="$2"
    local expected_result="$3"

    echo -e "${YELLOW}测试: $test_name${NC}"
    echo "命令: $command"

    if [[ "$command" == *"--api-key"* ]] || [[ "$command" == *"https://api.openai.com"* ]]; then
        echo -e "${YELLOW}跳过 (需要真实API密钥)${NC}"
        SKIPPED=$((SKIPPED + 1))
        return
    fi

    # 执行测试命令
    output=$(eval "$command" 2>&1)
    exit_code=$?

    if [ $exit_code -eq 0 ] || [[ "$output" == *"$expected_result"* ]]; then
        echo -e "${GREEN}✓ 通过${NC}"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}✗ 失败${NC}"
        echo "输出: $output"
        FAILED=$((FAILED + 1))
    fi
    echo
}

# 测试 JAR 文件是否存在
if [ ! -f "target/evalscope-*.jar" ]; then
    echo -e "${RED}错误: 未找到目标 JAR 文件"${NC}
    echo "请先运行: mvn clean package"
    exit 1
fi

JAR_FILE=$(ls target/evalscope-*.jar | head -n 1)

echo "使用 JAR 文件: $JAR_FILE"
echo

# 1. 帮助和版本信息测试
echo "=== 1. 帮助和版本信息测试 ==="
test_parameter "帮助信息" "java -jar $JAR_FILE --help" "EvalScope - AI Model Evaluation Framework"
test_parameter "版本信息" "java -jar $JAR_FILE --version" "EvalScope Java Version"
test_parameter "短格式帮助" "java -jar $JAR_FILE -h" "EvalScope - AI Model Evaluation Framework"

# 2. 基础参数测试
echo "=== 2. 基础参数测试 ==="
test_parameter "基本模型配置" "java -jar $JAR_FILE --model gpt-3.5-turbo --dataset general_qa" "Running evaluation with command line configuration"
test_parameter "URL 配置" "java -jar $JAR_FILE --url http://localhost:8080 --model test-model" "Running evaluation with command line configuration"

# 3. 性能参数测试
echo "=== 3. 性能参数测试 ==="
test_parameter "并发测试" "java -jar $JAR_FILE --concurrent 5 --number 10 --rounds 2" "Running evaluation with command line configuration"
test_parameter "工作线程配置" "java -jar $JAR_FILE --concurrent 3 --max-workers 10" "Running evaluation with command line configuration"

# 4. 请求参数测试
echo "=== 4. 请求参数测试 ==="
test_parameter "Token 设置" "java -jar $JAR_FILE --max-tokens 512" "Running evaluation with command line configuration"
test_parameter "温度设置" "java -jar $JAR_FILE --temperature 0.8" "Running evaluation with command line configuration"
test_parameter "Top-p 设置" "java -jar $JAR_FILE --top-p 0.95" "Running evaluation with command line configuration"
test_parameter "惩罚参数设置" "java -jar $JAR_FILE --frequency-penalty 0.5 --presence-penalty 0.3" "Running evaluation with command line configuration"
test_parameter "停止序列设置" "java -jar $JAR_FILE --stop 'User: Assistant:'" "Running evaluation with command line configuration"
test_parameter "流式传输" "java -jar $JAR_FILE --stream" "Running evaluation with command line configuration"
test_parameter "系统提示词" "java -jar $JAR_FILE --system '你是一个AI助手'" "Running evaluation with command line configuration"

# 5. 连接参数测试
echo "=== 5. 连接参数测试 ==="
test_parameter "连接超时设置" "java -jar $JAR_FILE --connect-timeout 60 --read-timeout 120" "Running evaluation with command line configuration"
test_parameter "重试配置" "java -jar $JAR_FILE --max-retries 5 --retry-delay 2000" "Running evaluation with command line configuration"
test_parameter "综合连接参数" "java -jar $JAR_FILE --max-workers 15 --connect-timeout 45 --read-timeout 90" "Running evaluation with command line configuration"

# 6. 模式参数测试
echo "=== 6. 模式参数测试 ==="
test_parameter "调试模式" "java -jar $JAR_FILE --debug --verbose" "Debug mode enabled"
test_parameter "试运行模式" "java -jar $JAR_FILE --dry-run" "Dry run mode"
test_parameter "短格式调试" "java -jar $JAR_FILE -d -v" "Debug mode enabled"
test_parameter "详细模式" "java -jar $JAR_FILE --verbose" "Verbose mode enabled"

# 7. 输出参数测试
echo "=== 7. 输出参数测试 ==="
test_parameter "输出路径设置" "java -jar $JAR_FILE --output results/custom_test.json" "Running evaluation with command line configuration"
test_parameter "输出格式设置" "java -jar $JAR_FILE --output-format csv" "Running evaluation with command line configuration"
test_parameter "结果保存开关" "java -jar $JAR_FILE --save-results true" "Running evaluation with command line configuration"

# 8. 数据集参数测试
echo "=== 8. 数据集参数测试 ==="
test_parameter "数据集路径" "java -jar $JAR_FILE --dataset-path data/test.jsonl" "Running evaluation with command line configuration"
test_parameter "数据集限制" "java -jar $JAR_FILE --dataset-limit 100" "Running evaluation with command line configuration"
test_parameter "数据集打乱" "java -jar $JAR_FILE --dataset-shuffle" "Running evaluation with command line configuration"

# 9. 评估参数测试
echo "=== 9. 评估参数测试 ==="
test_parameter "评估类型设置" "java -jar $JAR_FILE --evaluation-type stress" "Running evaluation with command line configuration"
test_parameter "指标设置" "java -jar $JAR_FILE --metrics latency,accuracy" "Running evaluation with command line configuration"
test_parameter "延迟指标开关" "java -jar $JAR_FILE --include-latency false" "Running evaluation with command line configuration"
test_parameter "准确性指标开关" "java -jar $JAR_FILE --include-accuracy false" "Running evaluation with command line configuration"

# 10. 速率限制参数测试
echo "=== 10. 速率限制参数测试 ==="
test_parameter "每秒请求数限制" "java -jar $JAR_FILE --requests-per-second 5" "Running evaluation with command line configuration"
test_parameter "每分钟请求数限制" "java -jar $JAR_FILE --requests-per-minute 300" "Running evaluation with command line configuration"

# 11. 系统参数测试
echo "=== 11. 系统参数测试 ==="
test_parameter "日志级别设置" "java -jar $JAR_FILE --log-level DEBUG" "Running evaluation with command line configuration"
test_parameter "配置文件加载" "java -jar $JAR_FILE --config examples/config.standard-benchmark.yaml" "Running evaluation"

# 12. 综合测试
echo "=== 12. 综合测试 ==="
test_parameter "完整参数组合" "java -jar $JAR_FILE \\
  --url http://localhost:8080 \\
  --model test-model \\
  --dataset test \\
  --concurrent 3 \\
  --number 10 \\
  --max-tokens 256 \\
  --temperature 0.5 \\
  --max-workers 10 \\
  --debug \\
  --output results/comprehensive_test.json \\
  --evaluation-type standard" "Running evaluation with command line configuration"

# 13. 边界条件测试
echo "=== 13. 边界条件测试 ==="
test_parameter "极端温度值" "java -jar $JAR_FILE --temperature 2.0" "Running evaluation with command line configuration"
test_parameter "零并发" "java -jar $JAR_FILE --concurrent 0" "Running evaluation with command line configuration"
test_parameter "负数值" "java -jar $JAR_FILE --max-tokens -100" "Running evaluation with command line configuration"

# 14. 参数格式测试
echo "=== 14. 参数格式测试 ==="
test_parameter "布尔值格式" "java -jar $JAR_FILE --save-results true --stream false" "Running evaluation with command line configuration"
test_parameter "等号格式" "java -jar $JAR_FILE --concurrent=5 --temperature=0.8" "Running evaluation with command line configuration"

# 测试结果汇总
echo "=== 测试结果汇总 ==="
echo -e "测试时间: ${GREEN}$(date)${NC}"
echo -e "通过测试: ${GREEN}$PASSED${NC}"
echo -e "失败测试: ${RED}$FAILED${NC}"
echo -e "跳过测试: ${YELLOW}$SKIPPED${NC}"
echo -e "总测试数: $((PASSED + FAILED + SKIPPED))"

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过!${NC}"
    exit 0
else
    echo -e "${RED}✗ 部分测试失败${NC}"
    exit 1
fi