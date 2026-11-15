# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## Build & Run Commands

```bash
# Full build (compile all modules, skip tests)
mvn clean install -DskipTests

# Full build with tests
mvn clean install

# Build a specific service module
mvn clean install -pl approval-service -am -DskipTests

# Build a specific sub-module (e.g., file-domain)
mvn clean install -pl file-service/file-domain -am -DskipTests

# Run tests for a specific module
mvn test -pl file-service/file-infrastructure

# Run a single test class
mvn test -pl file-service/file-infrastructure -Dtest=ParseFlowIntegrationTest

# Run a single test method
mvn test -pl file-service/file-infrastructure -Dtest=ParseFlowIntegrationTest#testSpecificMethod

# Compile only (no install)
mvn compile -DskipTests

# Run a service (from its starter module)
mvn spring-boot:run -pl approval-service/approval-starter
```

JDK 25 with `--enable-preview` is required. Annotation processors: Lombok, MapStruct, MyBatis-Flex.

## Architecture Overview

This is a **DDD + Hexagonal Architecture** Spring Cloud microservices project. Every business service strictly follows the same layered module structure with unidirectional dependency flow:

```
types → domain → api → application → adapter → infrastructure → starter
```

### Dependency Rules

- **types**: Domain primitives (ID types only). Depends on `shared-types`.
- **domain**: Entities, value objects, aggregate roots, domain services, SPI interfaces. Depends on `shared-domain` + `xxx-types`. **Domain layer must be pure** — no Spring annotations, no DB annotations, no JSON annotations. Only Lombok is allowed.
- **api**: DTOs, Commands, Queries, `@HttpExchange` interfaces. Depends on `shared-api` + `xxx-types`.
- **application**: Application services, orchestration. Depends on `xxx-api` + `xxx-domain` + `shared-*starter`.
- **adapter**: Controllers, MapStruct converters. Depends on `xxx-api` + `xxx-application`.
- **infrastructure**: Repository implementations, Mappers, DOs, data access. Depends on `xxx-domain` + `shared-*starter`.
- **starter**: Boot class, `application.yml`, packaging entry. Depends on `xxx-adapter` + `xxx-infrastructure`.

### Core Architectural Principles

1. **Dependency Inversion**: domain layer depends on nothing in application/infrastructure; decoupled through SPI (Gateway interfaces, Repository interfaces).
2. **Strong-Typed IDs**: All IDs implement `Identifier<?>` with `@IdDefinition` annotations declaring generation strategy (ULID/UUID_V7/SEGMENT).
3. **Aggregate Root Domain Events**: Events registered via `registerDomainEvent()`, consumed via `getDomainEvents()` then published.
4. **Cross-Aggregate ID References**: Direct object references between aggregates are forbidden — must use ID references.
5. **Repository Contract**: All repositories implement `Repository<T extends AggregateRoot<ID>, ID extends Identifier<?>>` with `load`/`save`/`delete`/`loadAll` methods.

### Exception Hierarchy

```
BaseException → DomainException / BusinessException / SystemException
```
Each carries an `ErrorDefinition` with structured error codes.

## Top-Level Modules

| Module | Role | DDD Layers |
|---|---|---|
| `demo-shared` | Shared infrastructure (26 sub-modules) | 3 tiers: `shared-lib` (domain primitives), `shared-core` (starter cores), `shared-starter` (auto-config) |
| `business-core-kernel` | Business process orchestration engine | 6 layers (no starter; consumed as library) |
| `file-service` | Excel parsing/export, template management | 7 layers |
| `approval-service` | Approval flow & instance management | 7 layers |
| `integration-service` | External system integration (5564 trade code) | 6 layers (no types layer) |
| `annuity-service` | Annuity business — built on business-core-kernel | 7 layers |
| `auth-service` | Authentication & authorization (sa-token) | 7 layers |
| `internet-bff` | Internet channel BFF (route, aggregate, adapt) | 5 layers (api/application/adapter/infrastructure/starter) |
| `intranet-bff` | Intranet channel BFF | 5 layers |
| `internet-gateway` | Internet channel gateway (auth, encrypt, route) | Single module |
| `intranet-gateway` | Intranet channel gateway | Single module |
| `demo-gateway` | API gateway with custom filters | Single module |

## demo-shared Structure (26 Sub-Modules)

`demo-shared` is organized in 3 tiers:

**shared-lib** (domain primitives, no Spring):
- `shared-types`, `shared-exception`, `shared-domain`, `shared-api`, `shared-utils`
- `shared-crypto`, `shared-json`, `shared-lock`
- `gateway-shared` (gateway common components), `bff-shared` (BFF common components)

**shared-core** (starter core logic, no auto-configuration):
- `shared-cache-core`, `shared-client-core`, `shared-event-core`, `shared-id-core`
- `shared-logging-core`, `shared-pdf-core`, `shared-web-core`

**shared-starter** (auto-configuration via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):
- `shared-cache-starter` (Caffeine L1 + Redisson L2 + distributed lock)
- `shared-client-starter` (Retrofit + Logbook)
- `shared-crypto-starter`, `shared-event-starter` (domain events → integration events → MQ)
- `shared-id-starter` (ULID/UUID_V7/SEGMENT, provides MySQL + PG schemas)
- `shared-logging-starter`, `shared-pdf-starter`, `shared-web-starter`, `shared-permission-starter`

## business-core-kernel: Business Orchestration Engine

This is the core engine providing a standard "business application lifecycle" pipeline. Business lines (annuity, etc.) extend it by implementing SPI interfaces — **never modify kernel code**.

### Three-Phase Orchestration

1. **Form Upload & Async Parsing**: `BusinessFormAppService` orchestrates `confirmUpload → triggerParsing → handleParsingResult`, spawning multiple `BusinessApplication` aggregates from one form.
2. **Application Flow Orchestration**: `FlowOrchestrationService.advanceStep(appId)` drives step execution: load aggregate → build config context → query step route → execute pre-extensions → execute main handler → execute detail processors → execute post-extensions → transit to next step → persist + publish events.
3. **Auto-Advance & Suspend**: `ApplicationSpawnedListener` auto-pushes new applications; `StepAutoAdvanceListener` auto-advances `SYSTEM_TASK` steps, suspends on `USER_TASK`.

### SPI Extension Points

- **`StepActionHandler`**: Step main processor (`handlerName()` → config JSON `mainProcessor`). Built-in: `defaultFormParsingHandler`, `planMaterialPreparationHandler`.
- **`StepExtensionAction`**: Pipeline extension action (`actionName()` → config JSON `preExtensions/postExtensions`). Supports VALIDATION/ENRICHMENT/NOTIFICATION/AUDIT.
- **`BusinessFactExtractor`**: Extracts business-specific facts into `BusinessMetaContext.extensionFacts` for SpEL/rules.

### Batch Processing Base Classes (in `business-core-application/extension/`)

- **`AbstractCursorPagingDetailAction`**: Cursor-based paging for UPDATE-heavy batch processing. Supports checkpoint resume.
- **`AbstractStreamingDetailAction`**: Streaming read for read-only/computation scenarios. Forces `REQUIRES_NEW` transaction for batch writes to avoid deadlock.
- **`AbstractJsonStreamIngestionAction`**: JSON stream ingestion from file service. Jackson `JsonParser` for OOM safety, single-row & batch-level exception isolation, degradation to single-row retry on batch failure.

### Anti-Corruption Layer Gateways (domain layer SPI)

- `BusinessConfigGateway`: Query step routes, material rules, extractor configs, form parsing configs.
- `ConditionEvaluationGateway`: SpEL expression evaluation (implemented by `SpelConditionEvaluationGateway`).
- `FileIntegrationGateway`: Trigger async parsing, stream-download files, request upload tokens.

### StepPipelineExecutor

Executes extension action chains: sorted by `order` → SpEL condition filter → sync/async dispatch → context mutation via `ExtensionExecutionResult.mutations()` → critical vs non-critical failure handling.

## Key Implementation Patterns

### MapStruct with Record Types

Domain objects use `record` with private constructors. MapStruct `@BeanMapping(factoryMethod="reconstitute")` is not supported. Pattern:
- `toDomain`: Use `default` methods, manually call `XxxDomain.reconstitute(...)`.
- `toDO`: Use `expression = "java(...)"`.
- ID `Long → String`: Use `String.valueOf(...)`.

### Repository Cascading Delete

Delete child tables before parent (e.g., approval records → executions → instance).

### Application Service Orchestration Pattern

Every use case follows: load aggregate → invoke behavior method → `repository.save(aggregate)` → `aggregate.getDomainEvents().forEach(eventBus::publish)` → `aggregate.clearDomainEvents()`.

## Tech Stack

| Technology | Version |
|---|---|
| JDK | 25 (`--enable-preview`) |
| Spring Boot | 3.5.14 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba (Nacos) | 2025.0.0.0 |
| MyBatis-Flex | 1.11.5 |
| Redisson | 4.3.1 |
| RocketMQ Spring Boot Starter | 2.3.6 |
| MapStruct | 1.6.3 |
| Lombok | 1.18.46 |
| Apache Fesod (Excel) | 2.0.2-incubating |
| Aviator / QLExpress4 | 5.9.0 / 4.1.2 |
| Fury (serialization) | 0.10.3 |
| Retrofit | 3.3.0 |
| Logbook | 4.0.4 |
| OpenHtmlToPdf | 1.1.36 |
| sa-token | 1.45.0 |
| Kona Crypto | 1.0.15 |

## Databases

- PostgreSQL: file-service (JSONB for nested structures), preferred for new services.
- MySQL: approval-service (both MySQL and PG schemas provided for shared starters).
- OceanBase: supported via `oceanbase-client` dependency.
- Shared starters provide dual `schema-mysql.sql` / `schema-pg.sql` DDL files.

## Known Gaps

- `business-core-api` now contains `@HttpExchange` APIs, CQE objects and DTOs (e.g. `BusinessProgressApi`, `AdvanceStepCommand`); `business-core-adapter` provides controllers, MapStruct converters and auto-configuration.
- `BusinessConfigGateway` still has no kernel default implementation — each business service must implement it. `FileIntegrationGateway` is provided by kernel's `FileServiceIntegrationGateway` (`@ConditionalOnProperty`, overridable by business services).
- approval-service query methods (`listApprovalFlows`/`listMyPendingApprovals`/`getApprovalHistory`) are implemented (batches 53–55); `RoleUserGatewayImpl` queries auth-api `PlanDirectoryApi` for SAME_PLAN / LEVEL_UP node resolution (batches 68–69). Organization-directory backing is an optional in-memory `ProductGateway` (disabled by default, `StubProductGateway` fallback); external master-data integration is out of repo scope. See `docs/superpowers/specs/2026-11-14-remaining-work-inventory.md` for the current gap list.

## Agent skills

### Issue tracker

本仓库 issue 以本地 markdown 文件形式存放在 `.scratch/` 下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用五个标准 triage 标签：needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix。详见 `docs/agents/triage-labels.md`。

### Domain docs

Single-context 布局：根目录 `CONTEXT.md` + `docs/adr/`。详见 `docs/agents/domain.md`。
