# PR #102 评审记录

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| PR | [#102 test: 补齐共享内核与问答测试，核心聚合行覆盖 100%、qa-domain 99.0%（Lab7 Step1/Step2）](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/102) |
| 作者 | 秦悦（`qy2026932`） |
| 评审人 | 李增艳（`LZY901-art`）、邹园园（`zouyuanyuan27`）、王钰滢（`wtr999-jpg`） |
| 评审日期 | 2026-11-26（首轮）、2026-11-28（复核） |
| 变更规模 | 21 个文件：17 个测试类 + 3 份质量/冒烟文档 + 2 份既有评审记录 |
| 评审结论 | 可合并，无阻塞项；单测本地全绿、覆盖率达标；集成测试未在容器环境实跑通过，按 ADR-010 记为观察项 |

## 一、结论

共享内核、问答领域/应用层与身份应用层测试补齐到位，既有 identity / qa 用例全部保留，零删除、零改松断言。断言是实的：`ArgumentCaptor` 校验事件字段、审计记录、缓存失效次数并 `verifyNoMoreInteractions`，不是为覆盖率凑数。

评审人本地实跑结果（口径以各评审人实测为准）：

- 邹园园复核（head `c594ae1`）：shared-kernel / qa-domain / qa-application / identity-application 共 255 例单测全绿，无失败无错误；行覆盖 identity-app 99.5%、qa-domain 99.0%、qa-app 99.0%、shared 98.3%（PR 描述 shared 写 98.4%，四舍五入差异）。
- 王钰滢本地：`mvn test -pl co-shared-kernel,co-qa/co-qa-domain,co-qa/co-qa-application,co-identity/co-identity-application -am` 172 用例、0 失败；`co-bootstrap` 的 `QuestionInteractionIT` 编译通过。
- 邹园园另一轮：shared-kernel + qa 三模块 233 例全绿；shared-kernel 98.3%、qa-domain 99.0%、qa-application 99.0%，Question/Answer/Comment/Vote 四个聚合 100%。

声誉 4 个重叠文件已在 `c594ae1` 回退，与 #106 不再冲突；IT 未在流水线验证一事已在 D-08 如实登记，没有虚报通过。CI 后端 / 前端 success，无冲突，规则上已有 Approve 即可合。

## 二、建议项（不阻塞合并）

### 🟡 建议改

**1. `ErrorCodeContractTest` 模块职责越界、判据偏弱**（`co-qa-domain/.../ErrorCodeContractTest.java`）

测试放在 qa-domain，却 `Files.walk` 整个 `backend/` 源码并读取 `docs/错误码.md`；qa-domain 单测本应只依赖本模块。判据上只要任一 `src/main` 的 `.java` 文本里出现 `"QA-1234"` 就算命中，写在注释或测试常量里也能过，且依赖文档反引号格式；只校验源码存在 `"CODE"` 字面量，不校验段位（1xxx/2xxx…）、异常类型与 HTTP 状态映射，文档码被改前缀时测不出来。

建议：上移到 bootstrap 测试源集；或排除注释行，改成匹配 `new XxxException("CODE"` 这种调用形态，并补一段前缀/段位一致性校验。

**2. D-06 描述与现状有出入**（`docs/quality/defect-list.md`）

缺陷清单写"根 pom minimum=0 且未配置 check 执行"，但 `co-qa-domain/pom.xml` 已绑定 `check-domain-coverage`、门槛 0.70。建议补一句"仅 qa-domain 已接入 check，其余领域模块门槛为 0"，避免 Step3 排查时误判全仓都没接门禁。

**3. 集成测试仍是"未验证"状态**

`QuestionInteractionIT` 只到编译；CI 集成测试步骤被 `continue-on-error: true` 掩盖（`Process completed with exit code 1`），PR 里也已标注。建议 Step3 收紧门禁前先在可运行 Docker 的 CI 或本地容器环境单独跑通一次两个 IT（`QuestionInteractionIT`、`CampusOverflowFlowIT`），并把测试报告或日志证据贴到 PR，别让"覆盖链路"的说法带上一个没跑过的类。

### 🟢 可选

**4.** 分支落后 main 12 个提交，虽能干净合并，建议先 rebase 再让 CI 重跑一次；这份覆盖率与 #106 分工开的，合并顺序变了读数要重新核对。

**5.** 提交噪音：声誉测试在 `47e0171` 加入、`c594ae1` 又回退，留在 13 个提交里。合之前 squash，diff 只剩真正新增的文件，评审和回溯都清爽。

## 三、验证方式

各评审人本地按多模块 `mvn test` 实跑单测（见第一节实测口径）；CI 后端构建与验证、前端构建均 success。集成测试按 ADR-010 本阶段记为观察项，Step3 解除 `continue-on-error` 后未通过转为阻塞项。

## 四、后续

- 建议项 1、2 归 Step3 门禁收口时一并处理；
- IT 实跑通过证据待容器环境产生后补登；
- 覆盖率读数若合并顺序变化，需在全仓口径下重新核对。
