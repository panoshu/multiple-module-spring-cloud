# 领域文档

工程类 skill 在探索代码库时应如何消费本仓库的领域文档。

## 探索之前，先读这些

- 仓库根目录下的 **`CONTEXT.md`**，或
- 仓库根目录下的 **`CONTEXT-MAP.md`**（如果存在）：它指向每个 context 各自的 `CONTEXT.md`。阅读与主题相关的每一个。
- **`docs/adr/`**：阅读涉及你即将工作区域的 ADR。在 multi-context 仓库中，还要检查 `src/<context>/docs/adr/` 中 context 范围内的决策。

如果这些文件中有任何一个不存在，就**静默继续**。不要指出它们的缺失；不要事先建议创建它们。`/domain-modeling` skill（通过 `/grill-with-docs` 和 `/improve-codebase-architecture` 触达）会在术语或决策真正得到解决时按需创建它们。

## 文件结构

Single-context 仓库（大多数仓库）：

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

Multi-context 仓库（根目录存在 `CONTEXT-MAP.md`）：

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## 使用词汇表的词汇

当你的输出要命名一个领域概念时（在 issue 标题、重构提案、假设、测试名称中），使用 `CONTEXT.md` 中所定义的术语。不要滑向词汇表明确回避的同义词。

如果你需要的概念尚未出现在词汇表中，这是一个信号：要么你在发明项目并不使用的语言（重新考虑），要么确实存在缺口（为 `/domain-modeling` 记下它）。

## 标记 ADR 冲突

如果你的输出与现有 ADR 相矛盾，就明确地提出来，而不是静默覆盖：

> _与 ADR-0007（event-sourced orders）相矛盾，但值得重新考虑，因为……_
