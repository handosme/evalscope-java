# EvalScope Java 命令行使用指南

本指南介绍了 EvalScope Java 版本的所有命令行参数，实现了完整的参数支持功能。

## 基本用法

```bash
java -jar evalscope-1.0.0.jar [parameters]
```

## 参数类别

### 基础测试参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--url` | 模型服务URL | String | - | `--url https://api.openai.com/v1` |
| `--model` | 要评估的模型名称 | String | - | `--model gpt-3.5-turbo` |
| `--api-key` | API密钥 | String | - | `--api-key sk-xxx` |
| `--dataset` | 评估数据集名称 | String | - | `--dataset general_qa` |

### 性能测试参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--concurrent` | 并发请求数 | Integer | 1 | `--concurrent 10` |
| `--number` | 每轮请求数量 | Integer | 1 | `--number 100` |
| `--rounds` | 测试轮次 | Integer | 1 | `--rounds 5` |

### 请求参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--max-tokens` | 最大响应token数 | Integer | 2048 | `--max-tokens 1000` |
| `--temperature` | 采样温度 | Double | 0.7 | `--temperature 0.8` |
| `--top-p` | Top-p采样 | Double | 0.9 | `--top-p 0.95` |
| `--frequency-penalty` | 频率惩罚 | Double | 0.0 | `--frequency-penalty 0.5` |
| `--presence-penalty` | 存在惩罚 | Double | 0.0 | `--presence-penalty 0.5` |
| `--stop` | 停止序列 | String | - | `--stop "User:,Assistant:"` |
| `--stream` | 启用流式传输 | Boolean | false | `--stream` |
| `--system` | 系统提示词 | String | - | `--system "你是一个有用的AI助手"` |

### 连接池参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--max-workers` | 最大工作线程数 | Integer | 10 | `--max-workers 20` |
| `--connect-timeout` | 连接超时时间(秒) | Integer | 30 | `--connect-timeout 60` |
| `--read-timeout` | 读取超时时间(秒) | Integer | 60 | `--read-timeout 120` |
| `--max-retries` | 最大重试次数 | Integer | 3 | `--max-retries 5` |
| `--retry-delay` | 重试延迟(毫秒) | Integer | 1000 | `--retry-delay 2000` |

### 测试模式参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--debug`, `-d` | 调试模式 | Boolean | false | `--debug` |
| `--dry-run` | 试运行模式 | Boolean | false | `--dry-run` |
| `--verbose`, `-v` | 详细输出 | Boolean | false | `--verbose` |

### 输出参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--output` | 输出文件路径 | String | - | `--output results/my_test.json` |
| `--output-format` | 输出格式 | String | json | `--output-format csv` |
| `--save-results` | 是否保存结果 | Boolean | true | `--save-results true` |

### 数据集参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--dataset-path` | 数据集文件路径 | String | - | `--dataset-path data/qa.jsonl` |
| `--dataset-limit` | 数据集限制数量 | Integer | - | `--dataset-limit 1000` |
| `--dataset-shuffle` | 是否打乱数据集 | Boolean | false | `--dataset-shuffle` |

### 评估参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--evaluation-type` | 评估类型 | String | standard | `--evaluation-type stress` |
| `--metrics` | 评估指标 | String | - | `--metrics latency,accuracy` |
| `--include-latency` | 是否包含延迟指标 | Boolean | true | `--include-latency true` |
| `--include-accuracy` | 是否包含准确性指标 | Boolean | true | `--include-accuracy true` |

### 认证参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--auth-type` | 认证类型 | String | - | `--auth-type bearer` |
| `--auth-token` | 认证令牌 | String | - | `--auth-token xxxx` |

### 速率限制参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--requests-per-second` | 每秒最大请求数 | Integer | - | `--requests-per-second 10` |
| `--requests-per-minute` | 每分钟最大请求数 | Integer | - | `--requests-per-minute 600` |

### 系统参数

| 参数 | 描述 | 类型 | 默认值 | 示例 |
|------|------|------|--------|------|
| `--config` | 配置文件路径 | String | - | `--config config.yaml` |
| `--log-level` | 日志级别 | String | INFO | `--log-level DEBUG` |
| `--help`, `-h` | 显示帮助信息 | Boolean | false | `--help` |
| `--version` | 显示版本信息 | String | - | `--version` |

## 使用示例

### 基础性能测试
```bash
java -jar evalscope.jar \
  --url https://api.openai.com/v1 \
  --model gpt-3.5-turbo \
  --api-key sk-your-key \
  --dataset general_qa \
  --concurrent 10 \
  --number 100 \
  --rounds 3
```

### 压力测试
```bash
java -jar evalscope.jar \
  --url https://api.example.com/v1 \
  --model llama-2-7b \
  --api-key your-key \
  --evaluation-type stress \
  --concurrent 50 \
  --number 1000 \
  --rounds 5 \
  --max-tokens 512 \
  --temperature 0.8
```

### 并发基准测试
```bash
java -jar evalscope.jar \
  --api-key your-key \
  --url https://api.openai.com/v1 \
  --model gpt-4 \
  --evaluation-type concurrent \
  --concurrent 20 \
  --number 50 \
  --rounds 2 \
  --max-workers 30 \
  --connect-timeout 60 \
  --read-timeout 120
```

### 使用配置文件
```bash
java -jar evalscope.jar --config config.yaml --evaluation-type longevity
```

### 调试模式
```bash
java -jar evalscope.jar \
  --url https://api.example.com/v1 \
  --api-key your-key \
  --debug \
  --verbose \
  --dry-run \
  --concurrent 1 \
  --number 5
```

### 自定义请求参数
```bash
java -jar evalscope.jar \
  --url https://api.example.com/v1 \
  --api-key your-key \
  --model claude-3-haiku \
  --temperature 0.5 \
  --top-p 0.9 \
  --max-tokens 2048 \
  --frequency-penalty 0.5 \
  --presence-penalty 0.3 \
  --system "你是一个专业的AI助手" \
  --concurrent 5 \
  --number 50
```

### 输出结果到指定文件
```bash
java -jar evalscope.jar \
  --config config.yaml \
  --output results/october_tests.json \
  --output-format json \
  --save-results true
```

## 获取帮助

运行以下命令获取所有参数的帮助信息：
```bash
java -jar evalscope.jar --help
```

或者简写形式：
```bash
java -jar evalscope.jar -h
```

## 版本信息

获取版本信息：
```bash
java -jar evalscope.jar --version
```

## 注意事项

1. **API密钥安全**：不要在命令行中直接暴露API密钥，可以使用环境变量或配置文件
2. **并发设置**：合理设置并发数，避免对目标服务造成过大压力
3. **超时设置**：根据网络环境和模型响应时间合理设置连接和读取超时
4. **结果保存**：确保有足够的磁盘空间保存评估结果
5. **速率限制**：遵守目标服务的速率限制要求，合理设置 `requests-per-minute` 参数

## 故障排查

使用 `--debug` 和 `--verbose` 参数可以帮助诊断问题：
```bash
java -jar evalscope.jar --debug --verbose [其他参数]
```

在试运行模式下测试配置是否正确：
```bash
java -jar evalscope.jar --dry-run [其他参数]
```

## 输出结果

评估结果默认保存在以下位置：
- 基础测试：`results/standard/`
- 压力测试：`results/stress/`
- 并发测试：`results/concurrent/`
- 长期测试：`results/longevity/`
- 对比测试：`results/comparative/`

结果文件格式支持 JSON、CSV、XML，可通过 `--output-format` 参数指定。