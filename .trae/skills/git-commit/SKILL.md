---
name: "git-commit"
description: "Drafts and validates Conventional Commits messages from staged changes. Invoke when user asks to commit, says '提交', 'commit', or before running git commit. Ensures messages comply with 09-提交信息规范.md."
---

# Git Commit Message Helper

基于 [09-提交信息规范.md](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/.trae/rules/09-提交信息规范.md) 生成和校验
Conventional Commits 格式的提交信息。

## When to Use

**触发场景（任一即触发）：**

- 用户说"提交"、"commit"、"git commit"、"保存到 git"
- 用户要求暂存并提交变更
- 在完成一个任务/功能/修复后准备提交

**不触发：**

- 用户仅查看 git status / diff / log
- 用户仅暂存文件（git add）不提交
- 用户执行 git reset / revert / merge / rebase 等非 commit 操作

## The Process

### Step 1: 收集变更上下文

**并行执行以下命令（无依赖关系）：**

```bash
# 1. 查看暂存区状态
git status --short

# 2. 查看暂存区 diff 统计
git diff --cached --stat

# 3. 查看最近 5 条提交信息（保持风格一致）
git log --oneline -5
```

**如果暂存区为空**：提示用户先 `git add`，停止。 **如果暂存区有未暂存的修改**：询问用户是否要一并提交。

### Step 2: 分析变更内容

基于 `git diff --cached` 的实际内容，判断：

1. **变更类型（type）**：按优先级选择最主要的类型

- `feat` > `fix` > `refactor` > `perf` > `docs` > `test` > `build` > `ci` > `chore` > `style` > `revert`

2. **影响范围（scope）**：根据变更文件路径匹配 scope

   | 文件路径模式 | Scope |
         |--------------|-------|
   | `demo-shared/shared-types/**` | `shared-types` |
   | `demo-shared/shared-exception/**` | `shared-exception` |
   | `demo-shared/shared-domain/**` | `shared-domain` |
   | `demo-shared/shared-{xxx}-starter/**` | `shared-{xxx}-starter` |
   | `demo-shared/**`（跨多个子模块） | `shared` |
   | `demo-gateway/**` | `gateway` |
   | `business-core-kernel/business-core-{layer}/**` | `core-{layer}` |
   | `business-core-kernel/**`（跨多个子模块） | `kernel` |
   | `file-service/file-{layer}/**` | `file-{layer}` |
   | `file-service/**`（跨多个子模块） | `file-service` |
   | `approval-service/approval-{layer}/**` | `approval-{layer}` |
   | `approval-service/**`（跨多个子模块） | `approval-service` |
   | `integration-service/integration-service-{layer}/**` | `integration-{layer}` |
   | `integration-service/**`（跨多个子模块） | `integration-service` |
   | `annuity-service/annuity-{layer}/**` | `annuity-{layer}` |
   | `annuity-service/**`（跨多个子模块） | `annuity-service` |
   | `.trae/rules/**` | `rules` |
   | `.trae/skills/**` | `skills` |
   | `pom.xml`（仅依赖版本） | `deps` |
   | `**/application*.yml` | `config` |

3. **变更摘要（subject）**：用祈使语气概括主要变更

### Step 3: 生成提交信息

**格式：**

```
<type>(<scope>): <subject>
```

**Subject 规则：**

- 使用中文祈使语气：`新增`、`修复`、`重构`、`移除`、`补充`、`优化`
- 不超过 50 字符（中文按 2 字符计）
- 结尾不加句号
- 首字母不大写

**Body 规则（多文件变更时必须）：**

- 使用有序列表说明变更要点
- 每行不超过 72 字符
- 解释"为什么"而非"做了什么"

### Step 4: 校验提交信息

提交前必须通过以下校验：

| 校验项       | 规则                                                                     |
|--------------|--------------------------------------------------------------------------|
| type 合法性  | 必须是 feat/fix/refactor/perf/docs/test/build/ci/chore/style/revert 之一 |
| scope 合法性 | 必须是 09-提交信息规范.md 中定义的 scope                                 |
| subject 语气 | 必须是祈使语气，无句号结尾                                               |
| Header 长度  | 不超过 72 字符                                                           |
| BOM 字符     | 禁止包含 BOM（`﻿`）                                                       |
| Body 必要性  | 多文件变更必须有 Body                                                    |

**校验失败时**：指出问题并重新生成，不执行提交。

### Step 5: 执行提交

**PowerShell 环境约束：**

- ❌ 禁止使用 HEREDOC（`<<'EOF'`），PowerShell 不支持
- ✅ 使用多个 `-m` 参数传递 Header 和 Body

```bash
git commit -m "<type>(<scope>): <subject>" -m "<body line 1>" -m "<body line 2>"
```

**如果用户明确要求提交信息为英文**：遵循相同格式规则，subject 使用英文祈使语气。

### Step 6: 验证提交结果

```bash
git log --oneline -1
git status
```

确认提交成功且工作区状态符合预期。

## Common Patterns

### Pattern 1: 新增功能

```
feat(file-domain): 新增 Excel 导出决策服务

1. 新增 ExportDecisionService 领域服务
2. 校验失败时阻止导出执行
3. 新增 4 个单元测试覆盖决策逻辑
```

### Pattern 2: 修复缺陷

```
fix(shared-domain): 修复 Entity.equals() 类型安全问题

1. 增加 getClass() 校验确保不同类型实体不相等
2. id 为 null 时 hashCode 回退 System.identityHashCode
3. 重建构造函数补充 validateInvariants() 调用
```

### Pattern 3: 重构

```
refactor(shared): 规范化错误码体系并补齐公共基础模块单元测试

1. 错误码统一为 5 位纯数字格式
2. 删除错误消息中的 {} 占位符
3. 新增 560 个单元测试覆盖修改
```

### Pattern 4: 文档

```
docs(rules): 新增提交信息规范

1. 新增 09-提交信息规范.md
2. 定义 type/scope/subject/body 规范
3. 提供正反示例对照
```

### Pattern 5: 构建/依赖

```
build(deps): 升级 MyBatis-Flex 至 1.11.5

1. 父 pom.xml 新增 mybatis-flex.version 属性
2. 各 infrastructure 模块引用统一版本
```

## Anti-Patterns（禁止）

| ❌ 错误             | 原因               | ✅ 正确                             |
|---------------------|--------------------|-------------------------------------|
| `init`              | 无 type/scope/语义 | `chore: 初始化项目结构`             |
| `123`               | 完全无语义         | `refactor(core-domain): 重构包结构` |
| `update`            | 非法 type          | `refactor(xxx): 重构 xxx`           |
| `fix bug`           | 无 scope、笼统     | `fix(file-domain): 修复表头丢失`    |
| `feat:add`          | type 后无空格      | `feat(xxx): 新增 xxx`               |
| `feat(xxx): Added`  | 过去时/首字母大写  | `feat(xxx): 新增 xxx`               |
| `feat(xxx): 新增了` | 过去时"了"         | `feat(xxx): 新增 xxx`               |

## Edge Cases

### Case 1: 跨多个顶级服务

当变更涉及多个顶级服务（如 file-service + approval-service）：

- scope 使用 `monorepo` 或省略 scope
- 在 Body 中说明各服务的变更

```
refactor: 统一所有服务的错误码格式

1. file-service: FileErrorCodes 改为 5 位数字
2. approval-service: ApprovalDomainErrorCode 改为 5 位数字
3. integration-service: TradeErrorCode 改为 5 位数字
```

### Case 2: 仅修改配置

```
chore(config): 补齐 file-starter application.yml 配置

1. 新增数据源、Nacos、Redis 基础配置
2. 添加 shared.logging.obfuscate 最小化配置
```

### Case 3: 测试补充

```
test(shared-domain): 补充 Entity 基类单元测试

1. 新增 19 个测试覆盖 equals/hashCode/validateInvariants
2. 参数化测试覆盖 null id 和不同类型场景
```

### Case 4: 混合类型（feat + test）

按优先级选择最主要的 type，在 Body 中说明其他变更：

```
feat(kernel): 新增 QLExpress4 条件求值网关实现

1. 在 core-infrastructure 新增 QlExpressConditionEvaluationGateway
2. 通过 @ConditionalOnProperty 控制实现切换
3. 新增 12 个单元测试覆盖表达式求值场景
```

## Validation Checklist

提交前确认：

- [ ] type 是合法类型
- [ ] scope 是 09-提交信息规范.md 中定义的 scope
- [ ] subject 使用祈使语气，无句号结尾
- [ ] Header 行不超过 72 字符
- [ ] 无 BOM 字符
- [ ] 多文件变更已写 Body
- [ ] PowerShell 下使用多个 -m 参数（无 HEREDOC）
