# 环境矩阵（P0-00-5）

> 状态：local/test 已实测；prod 仍需在发布环境逐项验证（因此看板保持 🟡）。

| 能力 | local | test | prod |
|---|---|---|---|
| 配置中心/注册中心 | Nacos `127.0.0.1:8848` | 禁用（`spring.cloud.nacos.discovery.enabled=false`） | Nacos 集群（地址由环境注入） |
| 数据库 | PostgreSQL `127.0.0.1:5432/pgdb`，schema `schema_demo` | H2 内存库 + 测试 DDL | PostgreSQL 主备（连接串/凭据由密钥管理注入） |
| Redis | 单机 `127.0.0.1:6379`，db 0 | 排除 Redis 自动配置；按需 `@MockitoBean` | Redis 集群/哨兵；事件通道与 sa-token 共用实例或分库 |
| 跨服务事件 | RocketMQ 5.3.2 Proxy gRPC `127.0.0.1:8081`（`shared.event.rocketmq.enabled=true`，Redis pub/sub 关闭） | 事件模拟器 `core.kernel.event-simulator.enabled=true` | RocketMQ（`shared.event.rocketmq.enabled=true`）；Redis pub/sub 保留为可配置回滚通道 |
| 链路追踪 | `X-Trace-Id` 网关生成/透传；8 服务 logback 输出 traceId + kernel 业务 MDC | 同上（测试上下文） | 网关/服务同上；接入 APM 时以 Micrometer/OTel 替换本地生成器 |
| 文件服务 | 真实 file-service（`core.integration.file.base-url`） | MockFileIntegrationGateway / 内存引用 | 真实 file-service + 对象存储 |
| 权限强制 | `permission.enforcement.enabled=false`（仅本地脚本） | false/按测试上下文 | **必须 true**，`permission.session.signature-key` 从环境注入 |
| 会话签名 | `dev-session-signature-key-2026` | 测试密钥 | 密钥管理注入，禁止开发占位值 |
| 网关 | `--spring.profiles.active=dev`（dev+local） | 上下文测试（Redis 端口 mock） | `internet`/`intranet` 双实例，按渠道分组 |
| 启动方式 | `scripts/local/start-all-services.sh` | `mvn test` | 容器/进程编排 + 健康检查 |

## 发布前 prod 检查清单

1. `permission.enforcement.enabled` 未显式关闭；`PermissionReadinessValidator` 启动通过。
2. `permission.session.signature-key`、`file.token.secret-key`、数据库/Redis 凭据均由密钥管理注入。
3. Nacos/RocketMQ/Redis/PG 网络连通与 ACL 验证。
4. 网关只启用目标渠道；`/internal/**` 不对公网暴露。
5. 事件通道启用 RocketMQ；outbox 幂等/重试与 Redis 回滚开关（`shared.event.rocketmq.enabled` / `shared.event.redis.enabled`）已验证。
