# PR #103 评审记录

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| PR | [#103 docs(team): 团队代码评审清单 v1.0（Lab 7）](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/103) |
| 作者 | 张嘉雨（`ember216`） |
| 评审人 | 秦悦（`qy2026932`） |
| 评审日期 | 2026-11-26（第一轮）、2026-11-27（第二轮复核） |
| 变更规模 | 1 个文件，纯文档，+158/−0 |
| 评审结论 | 第一轮 COMMENTED（2 项建议合并前改掉）；第二轮 APPROVED |

## 一、第一轮：两处口径会让照做的人直接失败

结论：框架与四要素写法可以直接当 Step 4 的评审依据——跨上下文红线五条与 ADR-001、002、008 对得上，幂等、软删除、事件分支完整性、投票上限、错误码段位与 HTTP 映射都点到了，比一般模板细。但有两处按现状写会让人失败。

### 建议合并前改掉

**1. 第二节本地基线的 `-pl "$SPRINT_MODULES" -am`**

`SPRINT_MODULES` 是 `.github/workflows/ci.yml` 的 `env` 变量，本地 shell 不存在，复制到 PowerShell 或 bash 会展开为空，变成 `mvn -pl "" -am` 直接报错。这份基线是给评审人照着敲的，建议本地行写成显式模块列表（如 `-pl co-shared-kernel,co-qa/co-qa-domain -am`），另起一行注明"CI 中由 SPRINT_MODULES 注入"。

**2. 覆盖率一行"领域层行覆盖率不低于门禁 0.70"**

`backend/pom.xml` 的 `domain.coverage.minimum` 现在是 0，也没配 jacoco 的 `check` 执行，实际只出报告、不拦截（已登记 D-06，归 Step3）。写成"门禁"会让人以为不达标就红、会拦合并。建议改成"目标 0.70；Step3 启用 check 前只出报告不拦截（见 D-06）"。

### 建议改

**3. 开篇"与 SonarQube / JaCoCo / Checkstyle / ESLint 等自动门禁互补"**

仓库里没有 SonarQube、Checkstyle、SpotBugs 的任何配置，`backend/pom.xml` 只挂了 jacoco、surefire、failsafe，工作流也没接。建议改成实际在跑的：JaCoCo 覆盖率、ArchUnit 分层约束、ESLint（`--max-warnings=0`）、Prettier、TypeScript 编译。

**4. `scripts/gen-api.ts` 路径写全**

实际在 `frontend/scripts/gen-api.ts`，第 4 节第 5 条与 3.6 两处都建议写全，避免有人找不到。

**5. 缺 ADR-010 的口径**

清单多处强调"CI 全绿""不要把未执行说成已通过"，但本阶段 ADR-010 明确集成测试允许失败（`continue-on-error: true`），到 Step3 才收紧。两条并排出现，评审人很容易把 IT 未通过当阻塞项（#102 现在就卡在这）。建议在第二节末尾补一句：集成测试按 ADR-010 本阶段记为观察项，Step3 解除后转为阻塞项。

**6. 第五节"每份深度评审至少 5 条实质性意见"**

建议限定口径为"至少 5 条，其中 🟡 及以上不少于 3 条"，否则容易用 🟢 可选项凑数。

### 可选

**7.** 3.6 写"`.editorconfig` / Prettier 一致"，仓库有 `frontend/.prettierrc.json`、没有 `.editorconfig`，直接写 Prettier 配置路径更准。

**8.** 第五节评审记录可给落点：仓库已有 `docs/review/`，建议明确为 `docs/review/pr-<编号>-<评审人>.md`，省后续归档。

**9.** 3.1 的"教师初始 1000 分、学生 0 分"这类数值建议标注出处（ADR-004 或指导书页码），分制调整时不用满篇改。

## 二、第二轮：修订版复核（749b10a）

结论：前一轮的两处硬伤均已闭合，逐条核对通过后 Approve。

**修订到位项**

- 本地命令改成显式模块列表，不再依赖 `$SPRINT_MODULES`
- 覆盖率口径改为"目标 0.70，Step3 启用 jacoco check 前只出报告不拦截"，与 ADR-010 / D-06 一致
- 开篇工具改为仓库实际在跑的 JaCoCo / ArchUnit / ESLint / Prettier / TS 编译
- 评审记录落点 `docs/review/` 存在
- 3.1 初始分指向 `ReputationRulebook.initialPoints(Role)` 存在（TEACHER 1000 / 学生 0），投票上限 200 一致
- 引用路径均已核实：`docs/错误码.md`、`docs/ddd/glossary-v1.0.md`、`docs/adr`（001–010）、`frontend/scripts/gen-api.ts`、`frontend/.editorconfig`、`frontend/.prettierrc.json`

**残留 3 条非阻塞**

1. "合格意见"示例写"`ReputationLedgerService.record` 目前只按用户 + 原因查重、建议把 eventId 纳入查重键"，与主干不符：`record` 内部是 `ledger.exists(sourceEventId, userId, reason)`，eventId 已在查重键内。被当模板照抄会让人去找不存在的缺陷，建议换真实缺口或改成"假设"语气。
2. 3.5 的 `npm run typecheck / lint / build` 没注明要在 `frontend/` 目录执行，照抄到仓库根会报 ENOENT。
3. 3.1"投票每日上限按校园时区计数"建议补依据：数据库时间戳统一存 UTC 墙钟（`UtcTime`），只有定时任务用 `Asia/Shanghai`，不写清容易被误读成按 UTC 日切。

## 三、验证方式

逐条对照仓库现状核验：模块与目录结构、`docs/adr` 001–010 全文、`docs/错误码.md`、`frontend/scripts/gen-api.ts`、`frontend/.editorconfig`、`frontend/.prettierrc.json`、`ReputationRulebook`、`ReputationLedgerService`、`backend/pom.xml` 的 jacoco 配置、`.github/workflows/ci.yml` 的 `SPRINT_MODULES` 与集成测试步骤。

## 四、后续

清单可作为 Lab 7 Step 4 的评审依据；3 条残留建议合并后随手修，不阻塞。
