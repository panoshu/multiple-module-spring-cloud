# Issue tracker：本地 Markdown

本仓库的 issue 和 spec 以 markdown 文件形式存放在 `.scratch/` 中。

## 约定

- 每个功能一个目录：`.scratch/<feature-slug>/`
- spec 是 `.scratch/<feature-slug>/spec.md`
- 实现 issue 是每个 ticket 一个文件，位于 `.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 开始编号，绝不使用单个合并的 tickets 文件
- triage 状态以 `Status:` 行的形式记录在每个 issue 文件的顶部附近（角色字符串见 `triage-labels.md`）
- 评论和对话历史追加到文件底部 `## Comments` 标题之下

## 当 skill 说 "publish to the issue tracker" 时

在 `.scratch/<feature-slug>/` 下创建一个新文件（必要时创建目录）。

## 当 skill 说 "fetch the relevant ticket" 时

读取所引用路径下的文件。用户通常会直接传入路径或 issue 编号。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 是一个文件，每个 ticket 对应一个 **child** 文件。

- **Map**：`.scratch/<effort>/map.md`（Notes / Decisions-so-far / Fog 正文）。
- **Child ticket**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 开始编号，问题写在正文中。`Type:` 行记录 ticket 类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **Blocking**：顶部附近的一行 `Blocked by: NN, NN`。当它列出的每个文件都是 `resolved` 时，ticket 即解除阻塞。
- **Frontier**：扫描 `.scratch/<effort>/issues/` 中处于打开、未阻塞且未认领状态的文件；按编号取第一个。
- **Claim**：在进行任何工作之前设置 `Status: claimed` 并保存。
- **Resolve**：在 `## Answer` 标题下追加答案，设置 `Status: resolved`，然后把一个上下文指针（gist + 链接）追加到 `map.md` 中 map 的 Decisions-so-far。
