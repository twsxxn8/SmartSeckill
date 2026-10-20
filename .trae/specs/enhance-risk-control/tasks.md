# Tasks

- [x] Task 1: 修复速度特征采集断链，激活两个死评分项
  - [x] SubTask 1.1: 在 `RiskControlService.extractFeatures()` 中读取滑动窗口 ZSet 全部时间戳（升序），计算相邻差值的 `minIntervalMs` 与 `avgIntervalMs` 填充到 builder；窗口内不足 2 条记录时保持 -1
  - [x] SubTask 1.2: 在 `SeckillTokenUtil.generateToken()`（或 `SeckillService.generateSeckillToken`，以覆盖用户与机器人两条链路为准）中接入 `riskControlService.recordTokenGenerated(userId, activityId)`
  - [x] SubTask 1.3: 单元测试：构造 50ms 间隔的窗口数据断言 +40 生效；构造 token 40ms 间隔断言 +25 生效

- [x] Task 2: 风控决策日志异步落库（RabbitMQ）
  - [x] SubTask 2.1: 新增 `risk_decision_log` 表 DDL 到 `init.sql`（id, user_id, activity_id, client_ip, user_agent, risk_score, risk_level, action, reason, created_at，user_id+created_at 索引）
  - [x] SubTask 2.2: 新增 `RiskDecisionEvent`（可序列化消息体）、`RiskDecisionLog` 实体与 Mapper
  - [x] SubTask 2.3: `RabbitMQConfig` 新增风控决策队列/交换机/绑定；新增 `risk/consumer/RiskDecisionConsumer` 监听落库
  - [x] SubTask 2.4: `RiskControlService.evaluateRisk` 对 DELAY/VERIFY/BLOCK 决策发送 MQ 事件，try-catch 降级为 error 日志，不抛出

- [x] Task 3: VERIFY 验证放行通道
  - [x] SubTask 3.1: `RedisUtil` 新增 `RISK_VERIFIED_PREFIX = "risk:verified:"` 常量与 TTL（10 分钟）
  - [x] SubTask 3.2: 新增 `RiskController`：`POST /risk/verify`，校验请求中的人机验证答案（复用现有验证码模拟方案），通过后写入 `risk:verified:{userId}:{activityId}` 标记
  - [x] SubTask 3.3: `SeckillService` VERIFY 分支前置检查该标记：命中则放行并在日志标记"已验证放行"，未命中抛 `RISK_CONTROL_VERIFY`

- [x] Task 4: 黑名单累积惩罚与管理端接口
  - [x] SubTask 4.1: `RedisUtil` 新增 `RISK_BLACKLIST_PREFIX`、`RISK_BLOCK_COUNT_PREFIX` 常量（TTL 24h）
  - [x] SubTask 4.2: `evaluateRisk` 第一步前置黑名单检查：命中直接返回 BLOCK(score=100, reason="黑名单用户")
  - [x] SubTask 4.3: BLOCK 决策后计数自增（INCR + 首次设置 TTL），达 3 次写入黑名单键；提供 `addToBlacklist`/`removeFromBlacklist`/`isBlacklisted` 方法
  - [x] SubTask 4.4: 新增管理端接口（ADMIN 权限）：`GET /admin/risk/decisions` 决策日志分页查询、`GET /admin/risk/blacklist`、`POST /admin/risk/blacklist/{userId}`、`DELETE /admin/risk/blacklist/{userId}`

- [x] Task 5: 端到端验证
  - [x] SubTask 5.1: `mvn test` 全量单测通过（新增用例 + 回归）
  - [x] SubTask 5.2: 启动应用，调用 BotController 以 FAST 比例启动机器人军团，验证：FAST 机器人触发 +25/+40 加分、部分被 BLOCK、决策日志表有记录、连续 BLOCK 3 次后进入黑名单
  - [x] SubTask 5.3: 验证 `/risk/verify` 通过后同一用户秒杀放行

# Task Dependencies
- Task 2 的 SubTask 2.4 依赖 Task 1（决策数据来自修复后的评估）
- Task 3、Task 4 可基于 Task 1 并行开发
- Task 5 依赖 Task 1-4 全部完成
