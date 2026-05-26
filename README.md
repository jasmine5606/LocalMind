<div align="center">
  <h1>LocalMind</h1>
  <p>基于 LangChain4j Agent 的本地生活智能发现平台</p>
  <p>
    <img src="https://img.shields.io/badge/Spring_Boot-2.3.12-brightgreen" alt="Spring Boot"/>
    <img src="https://img.shields.io/badge/Java-11-orange" alt="Java"/>
    <img src="https://img.shields.io/badge/LangChain4j-0.31.0-blue" alt="LangChain4j"/>
    <img src="https://img.shields.io/badge/MySQL-5.7-lightgrey" alt="MySQL"/>
    <img src="https://img.shields.io/badge/Redis-7.0-red" alt="Redis"/>
  </p>
</div>

## 项目简介

本地生活优惠抢购平台，商户发布限时折扣套餐，用户浏览附近商户并参与秒杀。核心亮点是基于 **LangChain4j AiServices** 构建的 LLM Agent，用户可通过自然语言搜索（如"人均100以内有秒杀券的火锅店"）替代传统多条件筛选，同时承接订单咨询等智能客服场景。

## 技术架构

```
┌─────────────────────────────────────────────────────┐
│                    Controller 层                      │
│   ShopController  VoucherOrderController  ChatController  │
├─────────────────────────────────────────────────────┤
│                    Service 层                          │
│   ShopService  VoucherOrderService  ChatService(AiServices)  │
│   AgentTools(@Tool)  SmartRecommendService              │
├─────────────────────────────────────────────────────┤
│                    数据层                              │
│   Caffeine(L1) ←→ Redis(L2) ←→ MySQL                   │
│          ↕                   ↕                          │
│   Canal(binlog) → Kafka → 缓存同步 → 死信队列 → 钉钉告警    │
└─────────────────────────────────────────────────────┘
```

## 核心功能

### LLM Agent 智能搜索
- 基于 **LangChain4j AiServices** 构建，**Qwen-Max** 作为推理模型
- 封装 4 个 **@Tool** 工具：查商户、查订单、查库存、搜优惠
- **MessageWindowChatMemory** 维护 20 轮对话上下文，session 隔离
- Agent 自主拆解意图 → 调用工具链 → 聚合结果回复

### 秒杀系统
- **Lua 脚本** 原子化校验库存 + 限购 + 扣减，杜绝超卖
- **雪花算法** + Redis 自增序列生成唯一请求ID，设为订单表**唯一索引**
- **Redis ZSet** 延迟队列实现超时关单，**Redisson** 分布式锁保证原子性

### 多级缓存
- **Caffeine(L1) + Redis(L2)** 二级缓存，逻辑过期 + 互斥锁防击穿
- 自研**热Key探测**：每5分钟采样两级命中率，自动提升热点至本地缓存

### 数据一致性
- **Canal** 监听 MySQL binlog → **Kafka** 异步删缓存
- 消费失败**指数退避重试**(10s→30s→90s)，3次失败入死信队列钉钉告警
- 所有 Key 强制 **30min TTL** 兜底

### 限流风控
- 自研 **@RateLimiter** 注解 + **Redis ZSet 滑动窗口**
- 支持全局/IP/用户三维度独立限流，连续3次触发**自动封禁5分钟**

## 快速启动

### 环境要求
- JDK 11+
- Maven 3.6+
- Docker & Docker Compose

### 1. 启动中间件
```bash
docker-compose up -d
```

### 2. 初始化数据库
MySQL 启动后会自动执行 `db/huixiang_life.sql` 建表并导入初始数据。

### 3. 配置百炼 API Key
编辑 `src/main/resources/application.yaml`，填入你的[阿里云百炼](https://bailian.console.aliyun.com/) API Key：
```yaml
bailian:
  api-key: your-api-key-here
```

### 4. 启动应用
```bash
mvn spring-boot:run
```

应用启动后访问 `http://localhost:8081`。

## 项目结构

```
src/main/java/com/huixiang/
├── annotation/     # 自定义注解 (@RateLimiter)
├── aspect/         # AOP 切面 (限流、偏好追踪)
├── config/         # Spring 配置 (缓存、Canal、Redisson、MVC)
├── consumer/       # Redis Stream 消费者
├── controller/     # REST 接口
├── dto/            # 数据传输对象
├── entity/         # 数据库实体
├── listener/       # Kafka 监听器 (Canal binlog)
├── mapper/         # MyBatis-Plus Mapper
├── monitor/        # 热Key探测
├── service/        # 业务逻辑 + Agent 工具
├── task/           # 定时任务 (订单关单)
└── utils/          # 工具类
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 2.3.12, MyBatis Plus 3.4.3 |
| 数据库 | MySQL 5.7 |
| 缓存 | Redis 7 + Caffeine |
| 消息队列 | Kafka |
| 数据同步 | Canal (MySQL binlog) |
| AI/LLM | LangChain4j 0.31.0 + Qwen-Max (阿里云百炼) |
| 分布式锁 | Redisson |
| 限流 | 自研 @RateLimiter (Redis Lua + AOP) |
| 部署 | Docker Compose |
