# 完善 AI 规则风控 Spec

## Why
项目已有的规则引擎风控（RiskControlService）存在两处特征采集断链，导致核心评分项成为永不触发的死代码；同时 VERIFY/BLOCK 处置缺少后续通道：用户被拦截后无任何救济路径，高危脚本也无累积惩罚与审计能力。

## What Changes
- **修复速度特征采集断链（Bug 修复）**：
  - `extractFeatures()` 从未给 `RiskFeature.minIntervalMs / avgIntervalMs` 赋值，导致 `isInhumanSpeed()` 恒为 false，`SCORE_INHUMAN_SPEED`（+40）永不生效 → 从滑动窗口 ZSet 时间戳序列计算并填充
  - `recordTokenGenerated()` 全项目无调用方，导致 `tokenToExecuteMs` 恒为 -1，`SCORE_TOKEN_TOO_FAST`（+25）永不生效 → 接入 Token 生成路径（用户与机器人链路统一覆盖）
- **新增决策日志异步落库**：新增 `risk_decision_log` 表，BLOCK/VERIFY/DELAY 决策经 RabbitMQ 异步落库（填充空置的 `risk/consumer` 目录），不阻塞秒杀主链路
- **新增 VERIFY 验证放行通道**：新增 `POST /risk/verify` 人机校验接口，通过后写入短时已验证标记；秒杀主链路在 VERIFY 决策时检查该标记，已验证则放行
- **新增黑名单机制**：BLOCK 决策按用户累计计数，达阈值自动拉黑（Redis，TTL 24h）；`evaluateRisk` 前置黑名单检查直接 BLOCK；管理端提供黑名单查询/手动拉黑/解除接口
- **新增管理端风控查询接口**：决策日志分页查询、黑名单管理

## Impact
- Affected specs: 秒杀主链路（SeckillService 风控段）、AI 风控引擎（risk 模块）、管理端（admin）
- Affected code:
  - `risk/service/RiskControlService.java`（特征计算修复、黑名单前置检查、决策事件发送）
  - `risk/consumer/`（新增 RiskDecisionConsumer）
  - `risk/model/`（新增 RiskDecisionEvent、RiskDecisionLog 实体）
  - `risk/controller/`（新增 RiskController 验证接口）、`admin/`（新增管理端查询接口）
  - `common/util/RedisUtil.java`（新增风控相关 Key 常量）
  - `common/config/RabbitMQConfig.java`（新增风控决策队列）
  - `seckill/service/SeckillService.java`（VERIFY 放行检查）
  - `common/util/SeckillTokenUtil.java`（接入 recordTokenGenerated）
  - `resources/sql/init.sql`（新增 risk_decision_log 表 DDL）
- 不改动的部分：评分权重与阈值（30/50/80 分档）维持现状；前端 VERIFY 页面接入不在本次范围（后端接口先行）

## ADDED Requirements

### Requirement: 请求间隔特征真实生效
系统 SHALL 在每次风控评估时，从用户滑动窗口 ZSet（`risk:feature:user:{userId}:{activityId}`）的时间戳序列计算 `minIntervalMs` 与 `avgIntervalMs` 并填充到 `RiskFeature`；窗口内请求数不足 2 次时两者均为 -1，不触发速度评分。

#### Scenario: 脚本级请求间隔被识别
- **WHEN** 同一用户在同一活动窗口（60s）内连续两次请求间隔为 50ms
- **THEN** `isInhumanSpeed()` 返回 true，本次评分 +40

#### Scenario: 正常间隔不加分
- **WHEN** 窗口内请求间隔均 ≥80ms
- **THEN** 不触发 `SCORE_INHUMAN_SPEED` 加分

### Requirement: Token→执行间隔特征真实生效
系统 SHALL 在生成秒杀 Token 时（用户 `/seckill/token` 与机器人军团两条链路）调用 `RiskControlService.recordTokenGenerated` 记录时间戳；评估时计算 `tokenToExecuteMs`，间隔 >0 且 <100ms 时评分 +25。

#### Scenario: 瞬时执行被识别
- **WHEN** 用户拿到 Token 后 40ms 内发起秒杀执行
- **THEN** 评分 +25

### Requirement: 风控决策日志异步持久化
系统 SHALL 将 DELAY/VERIFY/BLOCK 决策（PASS 可选采样）封装为事件发送到 RabbitMQ 风控决策队列，由 `risk/consumer` 下的消费者异步写入 `risk_decision_log` 表；MQ 发送异常时降级为仅记录本地日志，不影响秒杀主链路。

#### Scenario: BLOCK 决策被落库
- **WHEN** 一次评估产生 BLOCK 决策
- **THEN** `risk_decision_log` 表中最终存在一条包含 userId、activityId、IP、评分、等级、动作、原因、时间的记录

#### Scenario: MQ 异常不阻塞主链路
- **WHEN** 决策事件发送 MQ 抛出异常
- **THEN** 秒杀流程按原决策继续执行，仅输出 error 日志

### Requirement: VERIFY 验证放行通道
系统 SHALL 提供 `POST /risk/verify` 接口：校验人机验证答案通过后，写入 Redis 标记 `risk:verified:{userId}:{activityId}`（TTL 10 分钟）。秒杀主链路在决策为 VERIFY 时检查该标记：存在则按 PASS 放行并在日志中记为已验证放行，不存在则抛出 `RISK_CONTROL_VERIFY`。

#### Scenario: 验证通过后秒杀放行
- **WHEN** 用户被判定 VERIFY → 调用 `/risk/verify` 通过 → 10 分钟内重新执行秒杀
- **THEN** 不再抛出 1402，请求进入后续库存扣减流程

#### Scenario: 未验证仍被拦截
- **WHEN** 用户被判定 VERIFY 且未通过验证
- **THEN** 抛出 `RISK_CONTROL_VERIFY`

### Requirement: 黑名单累积惩罚
系统 SHALL 在每次 BLOCK 决策后对用户计数（Redis `risk:block:count:{userId}`，TTL 24h）；累计达到 3 次时写入黑名单 `risk:blacklist:{userId}`（TTL 24h）。`evaluateRisk` 第一步前置检查黑名单：命中则直接返回 BLOCK（评分 100），不再提取特征。

#### Scenario: 多次 BLOCK 后自动拉黑
- **WHEN** 同一用户 24h 内第 3 次被 BLOCK
- **THEN** 写入黑名单，后续请求直接 BLOCK 且不再消耗特征提取资源

#### Scenario: 管理端手动解除
- **WHEN** 管理员调用解除黑名单接口
- **THEN** 删除 Redis 黑名单键与计数键，用户恢复按正常评分流程评估

### Requirement: 管理端风控查询
系统 SHALL 提供管理端接口（仅 ADMIN 角色）：决策日志按 userId/activityId/动作/时间范围分页查询；黑名单列表查询、手动拉黑、解除拉黑。

#### Scenario: 分页查询决策日志
- **WHEN** 管理员携带 userId 与分页参数查询
- **THEN** 返回该用户的决策日志分页结果，按时间倒序

## MODIFIED Requirements

### Requirement: 秒杀主链路风控处置
原逻辑：VERIFY 一律抛 `RISK_CONTROL_VERIFY`。
修改后：VERIFY 决策时先检查 `risk:verified:{userId}:{activityId}` 标记，命中则放行（视同 PASS），未命中才抛异常；BLOCK 保持直接抛 `RISK_CONTROL_BLOCKED`；DELAY 保持随机延迟 100-500ms。所有非 PASS 决策在返回前异步发送决策事件。

## REMOVED Requirements
无。
