# dependency:analyze 基线（P0-00-4，2026-11-15）

执行命令：`mvn -DskipTests dependency:analyze`（全仓 92 个模块，退出码 0）。

## 结论

- 构建可通过，无“used undeclared / unused declared”导致的编译失败。
- 绝大多数告警属于已知误报：
  - Spring Boot starter / autoconfigure：字节码未直接引用，但运行时装配必需。
  - `junit-jupiter` aggregator：测试实际使用 `junit-jupiter-api/params`，aggregator 被标记 unused。
  - MapStruct/Lombok 注解处理器：运行时/编译期需要但字节码无引用。
- 已处理的实际冗余：S-06（kernel cache starter）、S-07（kernel client starter）、
  AU-03（auth-application Caffeine/shared-event-core）、AU-04（auth-adapter→infrastructure）。
- 后续可选收敛：将各模块 `junit-jupiter` aggregator 改为 `junit-jupiter-api + params`；
  为新 starter 模块补齐显式 `shared-*` 依赖（当前依赖传递可编译）。

完整原始输出：`/tmp/integration/dependency-analyze-verbose.log`（本机执行产物，不入库）。
