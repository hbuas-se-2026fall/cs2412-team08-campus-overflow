# PR #5 评审记录 · `feature/qinyue` → `main`

> Lab 7 提交物 2「至少 4 份高质量 PR 评审记录」第 **2** 份（第 1 份见 `操作指南/06-PR3评审意见.md`）。
> 评审人：张嘉雨 `@ember216` ｜ 评审日期：2026-10-27 ｜ 复核日期：2026-10-27 14:10（PR 未更新，问题依旧）
> PR 内容：新增 `docs/team/hello-qinyue.md`（13 行，1 个 commit `25519be`）
> 评审结论：**Request changes**（3 条必改 + 3 条建议，共 6 条实质意见）

## 一、检查表（13 项）

| # | 检查项 | 结果 | 说明 |
| --- | --- | --- | --- |
| 1 | 路径符合 `docs/team/hello-<拼音>.md` | ✅ | `hello-qinyue.md` 正确 |
| 2 | 一级标题 `# 自我介绍 · 姓名` | ❌ | 首行是裸文本「秦悦」，无标题层级 |
| 3 | Public 仓库不出现学号 | ❌❌ | **第 2、6 行各写了一次完整学号**，红线 |
| 4 | GitHub 用户名 | ❌ | 缺 `@` 账号，无法指派任务 / 关联评审 |
| 5 | 班级与小组 | ❌ | 缺「计科2412班 · 第 5 组」 |
| 6 | 团队角色（PO/SM/Developer） | ◐ | 写的是「开发组员」，未对齐章程角色名 |
| 7 | 技能描述具体可验证 | ◐ | 「熟悉 Java 基础」过于笼统 |
| 8 | 「希望学到什么」 | ❌ | 缺 |
| 9 | 开发环境（OS / IDE / JDK / Node） | ❌ | 缺，影响 JDK 21 / Node 20 统一 |
| 10 | 三份个人交付物齐全 | ❌ | 只交 1 份，缺 `role-commitment-qinyue.md`、`skill-matrix-qinyue.md` |
| 11 | commit message `<type>: <说明>` | ✅ | `docs: add qinyue self introduction`（建议改中文） |
| 12 | 分支命名 `feature/<拼音>-<主题>` | ◐ | `feature/qinyue` 缺主题，建议 `-hello` |
| 13 | 无无关文件混入 | ✅ | 只新增 1 个文件 |

## 二、必改（3 条，阻塞合并）

1. **删除两处明文学号**。仓库 Public，学号属个人信息。团队内同一规则已第二次踩（王钰滢 PR #3 已改），评审必须指出。
2. **补 GitHub 用户名与团队角色**。没有 `@` 账号，SM 在看板 / Issue 里无法指派；角色名对齐章程 §0（`Developer` 或 `Developer 兼 QA`）。
3. **补齐另外两份个人交付物**。`role-commitment-qinyue.md`、`skill-matrix-qinyue.md`，Lab1 个人提交物要求齐备。

## 三、建议（3 条，不阻塞）

4. 补「希望在项目中学到什么」——验收时能看出是否真读过实验要求。
5. 补开发环境：OS / IDE / JDK 21 / Node 20，方便 SM 提前拉齐。
6. 技能描述加程度：「Java 基础」→「能写完整课程作业级程序，了解集合与异常处理」这类可验证说法。

## 四、可直接粘贴的 Review 正文

> 秦悦你好，文档结构清楚，负责角色和技能都写到了。有三处需要改一下，改完我这边就可以 Approve。
>
> **1）学号要删掉（这条比较硬）**
> 第 2 行和第 6 行都写了完整学号。咱们仓库是 Public 的，个人信息不适合放进来——王钰滢的 PR #3 也是这个问题，已经改成「已登记于纸质承诺书（Public 仓库不录入学号）」了，这里建议用同样写法。我下面挂了一个 suggestion，可以直接点 Apply。
>
> **2）补上 GitHub 用户名和团队角色**
> 现在缺 `@` 账号，SM 在看板和 Issue 里没法指派你。角色建议按章程 §0 的写法，写成 `Developer`（或 `Developer 兼 QA`）。
>
> **3）另外两份文件没看到**
> `role-commitment-qinyue.md` 和 `skill-matrix-qinyue.md` 这两个还没提交吧？王钰滢那次是一次 PR 交了三份，Lab1 的个人提交物要求是齐的，麻烦补到这个分支里。
>
> 另外三个小建议，不阻塞合并：补「希望学到什么」和开发环境（JDK 21 / Node 20），另外首行建议加个 `# 自我介绍 · 秦悦` 标题，技能那块可以写具体一点，比如能写到什么程度。

## 五、Suggestion 块（挂在第 1–3 行）

````
```suggestion
# 自我介绍 · 秦悦

- **姓名**：秦悦
- **学号**：已登记于纸质承诺书（Public 仓库不录入学号，需核对时找 SM 或查纸质版）
```
````

## 六、第 2 轮评审（2026-10-27 15:05，新提交 `7fea1bd`）

上一轮 3 条必改她都改了：学号两处已删除 ✅、角色写成 `Developer` ✅、两个缺失文件已补 ✅。
但仍有问题，**结论：Request changes（2 条必改 + 4 条建议）**。

### 必改 1｜GitHub 用户名写成了组长的账号

三个文件里都写成 `GitHub: @zouyuanyuan27` —— 这是 **PO 邹园园**的账号，不是你的。你的账号是 **`@QY2026932`**。
这个必须改，否则看板指派、PR Reviewer、Issue @ 全部会指到组长头上。三份文件都要改。

### 必改 2｜两个文件放错目录

```
docs/role-commitment-qinyue.md      →  docs/team/role-commitment-qinyue.md
docs/skill-matrix-qinyue.md         →  docs/team/skill-matrix-qinyue.md
```

王钰滢和张嘉雨的三份都在 `docs/team/` 下（`docs/team/hello-*.md` / `role-commitment-*.md` / `skill-matrix-*.md`），保持一致，组长收材料时不会漏。

### 可以直接贴给她的命令

```bash
git mv docs/role-commitment-qinyue.md docs/team/role-commitment-qinyue.md
git mv docs/skill-matrix-qinyue.md docs/team/skill-matrix-qinyue.md
# 然后把三份文件里的 @zouyuanyuan27 全部替换成 @QY2026932
git add -A && git commit -m "docs: 修正 GitHub 用户名并将承诺书与技能矩阵移入 docs/team"
git push origin feature/qinyue
```

### 建议（不阻塞合并）

1. `hello-qinyue.md` 首行加 `# 自我介绍 · 秦悦`，「个人信息 / 负责角色 / 技能」加 `##` 二级标题；
2. 补班级小组「计科2412班 · 第 5 组」（其他四人都有这一项）；
3. `skill-matrix-qinyue.md` 首行「 技能矩阵」前面有空格，表格行也带前导空格，Markdown 会被渲染成代码块，去掉行首空格；
4. 承诺书落款「姓名：qinyue」建议签中文名（其他人都是签中文名）。

### 第 3 轮核查（2026-10-27 15:25，提交 `9cb7eea`）→ **可 Approve**

全部到位：

| 检查项 | 结果 |
| --- | --- |
| 三份文件均在 `docs/team/` | ✅ `hello-` / `role-commitment-` / `skill-matrix-` `qinyue.md` |
| GitHub 账号三份一致且为本人 | ✅ 均为 `@QY2026932`（首轮误写成 PO 的 `@zouyuanyuan27`，已改） |
| 学号 / 长数字残留 | ✅ 全文无 |
| 团队角色 | ✅ `Developer` |

**可选 polishing（不阻塞）**：hello 首行加 `# 自我介绍 · 秦悦` 标题、给「个人信息 / 负责角色 / 技能」加 `##`；补班级小组（计科2412班 · 第 5 组）；技能矩阵首行空格去掉以免被渲染成代码块；承诺书落款改中文名。

> 合并仍由 SM 执行，我不点 Merge。

## 七、状态

| 项 | 状态 |
| --- | --- |
| 第 1 轮 Request changes | ☑ 2026-10-27 14:25 已提交 |
| 作者第 1 轮修改 | ☑ 2026-10-27（`7fea1bd`，学号已删 / 角色已改 / 两份文件已补） |
| 第 2 轮 Request changes | ☐ **待本人在网页提交**（本机无 `gh` CLI，且 github.com 网页访问被网络限制，无法代发） |
| 作者第 2 轮修改 | ☑ 2026-10-27（`9cb7eea`，账号三份全改、文件已移入 `docs/team`） |
| 结论 | ✅ **可以 Approve**（4 条可选建议不阻塞） |
| 复查并 Approve | ☐ 待她再次推送后 Re-request review |
| 由谁合并 | **不由我合并**，按章程 §5 由 SM（李增艳）Squash and merge |

> 操作步骤见 `操作指南/07-评审PR5-秦悦.md` 第七节。
