# Lab7 代码评审归档（#102 / #106 / #107）

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| 归档范围 | PR #102、#106、#107 三轮代码评审记录 |
| 归档人 | 李增艳（`LZY901-art`） |
| 评审日期 | 2026-11-26 ～ 2026-11-28 |
| 变更规模 | 3 份评审记录，纯文档 |
| 总体结论 | 三轮均无阻塞项；建议级意见按各节列出，门禁收口阶段跟进 |

## 一、PR #102 评审记录

| 项目 | 内容 |
| --- | --- |
| PR | [#102 test: 补齐共享内核与问答测试，核心聚合行覆盖 100%、qa-domain 99.0%（Lab7 Step1/Step2）](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/102) |
| 作者 | 秦悦（`qy2026932`） |
| 评审人 | 李增艳（`LZY901-art`）、邹园园（`zouyuanyuan27`）、王钰滢（`wtr999-jpg`） |
| 评审日期 | 2026-11-26（首轮）、2026-11-28（复核） |
| 变更规模 | 21 个文件：17 个测试类 + 2 份质量/冒烟文档 + 2 份既有评审记录 |
| 评审结论 | 可合并，无阻塞项；单测本地全绿、覆盖率达标；集成测试未在容器环境实跑通过，按 ADR-010 记为观察项 |

### 结论

共享内核、问答领域/应用层与身份应用层测试补齐到位，既有 identity / qa 用例全部保留，零删除、零改松断言。断言是实的：`ArgumentCaptor` 校验事件字段、审计记录、缓存失效次数并 `verifyNoMoreInteractions`，不是为覆盖率凑数。

评审人本地实跑结果（口径以各评审人实测为准）：

- 邹园园复核（head `c594ae1`）：shared-kernel / qa-domain / qa-application / identity-application 共 255 例单测全绿，无失败无错误；行覆盖 identity-app 99.5%、qa-domain 99.0%、qa-app 99.0%、shared 98.3%（PR 描述 shared 写 98.4%，四舍五入差异）。
- 王钰滢本地：`mvn test -pl co-shared-kernel,co-qa/co-qa-domain,co-qa/co-qa-application,co-identity/co-identity-application -am` 172 用例、0 失败；`co-bootstrap` 的 `QuestionInteractionIT` 编译通过。
- 邹园园另一轮：shared-kernel + qa 三模块 233 例全绿；shared-kernel 98.3%、qa-domain 99.0%、qa-application 99.0%，Question/Answer/Comment/Vote 四个聚合 100%。

声誉 4 个重叠文件已在 `c594ae1` 回退，与 #106 不再冲突；IT 未在流水线验证一事已在 D-08 如实登记，没有虚报通过。CI 后端 / 前端 success，无冲突，规则上已有 Approve 即可合。

### 建议项（不阻塞合并）

**🟡 1. `ErrorCodeContractTest` 模块职责越界、判据偏弱**（`co-qa-domain/.../ErrorCodeContractTest.java`）

测试放在 qa-domain，却 `Files.walk` 整个 `backend/` 源码并读取 `docs/错误码.md`；qa-domain 单测本应只依赖本模块。判据上只要任一 `src/main` 的 `.java` 文本里出现 `"QA-1234"` 就算命中，写在 main 源码注释或字符串常量里也能过，且依赖文档反引号格式；只校验源码存在 `"CODE"` 字面量，不校验段位（1xxx/2xxx…）、异常类型与 HTTP 状态映射，文档码被改前缀时测不出来。

建议：上移到 bootstrap 测试源集；或排除注释行，改成匹配 `new XxxException("CODE"` 这种调用形态，并补一段前缀/段位一致性校验。

**🟡 2. D-06 描述与现状有出入**（`docs/quality/defect-list.md`）

缺陷清单写"根 pom minimum=0 且未配置 check 执行"，但 `co-qa-domain/pom.xml` 已绑定 `check-domain-coverage`、门槛 0.70。建议补一句"仅 qa-domain 已接入 check，其余领域模块门槛为 0"，避免 Step3 排查时误判全仓都没接门禁。

**🟡 3. 集成测试仍是"未验证"状态**

`QuestionInteractionIT` 只到编译；CI 集成测试步骤被 `continue-on-error: true` 掩盖（`Process completed with exit code 1`），PR 里也已标注。建议 Step3 收紧门禁前先在可运行 Docker 的 CI 或本地容器环境单独跑通一次两个 IT（`QuestionInteractionIT`、`CampusOverflowFlowIT`），并把测试报告或日志证据贴到 PR，别让"覆盖链路"的说法带上一个没跑过的类。

**🟢 4.** 分支落后 main 12 个提交，虽能干净合并，建议先 rebase 再让 CI 重跑一次；这份覆盖率与 #106 分工开的，合并顺序变了读数要重新核对。

**🟢 5.** 提交噪音：声誉测试在 `47e0171` 加入、`c594ae1` 又回退，留在 13 个提交里。合之前 squash，diff 只剩真正新增的文件，评审和回溯都清爽。

### 验证方式

各评审人本地按多模块 `mvn test` 实跑单测（见第一节实测口径）；CI 后端构建与验证、前端构建均 success。集成测试按 ADR-010 本阶段记为观察项，Step3 解除 `continue-on-error` 后未通过转为阻塞项。

### 后续

- 建议项 1、2 归 Step3 门禁收口时一并处理；
- IT 实跑通过证据待容器环境产生后补登；
- 覆盖率读数若合并顺序变化，需在全仓口径下重新核对。

## 二、PR #106 评审记录

| 项目 | 内容 |
| --- | --- |
| PR | [#106 test(reputation): 补齐声誉模块边界测试](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/106) |
| 作者 | 邹园园（`zouyuanyuan27`） |
| 评审人 | 张嘉雨（`ember216`）、王钰滢（`wtr999-jpg`） |
| 评审日期 | 2026-11-28 |
| 变更规模 | 5 个文件：4 个 reputation-application 测试类 + 1 个 reputation-domain 测试类 |
| 评审结论 | 通过（APPROVED），未发现阻塞问题 |

### 结论

声誉模块边界测试补齐到位，断言都落到具体状态、winnerId、余额数值、过期时间、异常类型或 Mockito 交互，与生产实现逐条比对一致：账户不存在 REP-4001、流水越权 REP-3002、悬赏角色/关闭/积分 10~500/天数 1~14、重复结算 REP-2002、自领自赏 REP-1007、冻结/支出/退款余额推演、徽章阈值、学期划分、排行榜幂等、总榜/周期榜读取来源等。

评审人本地复跑：声誉模块 56 例全绿（0 失败/错误/跳过），编译通过；本 PR 新增 28 例，王钰滢本地 28 个用例全绿。

> 覆盖率口径说明：PR 描述应用层 91.7%、领域 89.3%、全仓 182 例；评审人本地仅复跑声誉模块 56 例，覆盖率数字与全仓用例口径未在本地复算，待全仓 verify 时核对。

### 建议项（不阻塞合并）

**🟡 1. `ReputationApiImplTest` 偏薄：缺账户口径未测**

评审当时记录：`summary` 缺失补 0、`reputations` 批量不补默认值，两者差异没有覆盖到，建议补一组无账户/空批量的用例，固定两种口径的返回形态。

> 现状核对（合并版本 `0e7bbf7`）：该类已含 `summary_账户缺失_声誉记为零`、`reputations_缺失用户不补默认值` 两组用例，上述两种口径均已覆盖；本条为评审当时漏看，保留作记录，不另列补测任务。

**🟡 2. 声誉模块未纳入 CI 测试范围**

CI 的 `SPRINT_MODULES` 目前不含 `co-reputation`，阶段 2（单元测试）与阶段 4（覆盖率门禁）只在列出的模块上跑，这批新测试在 CI 里实际不会被执行，只有阶段 1 编译覆盖到。建议把 `co-reputation/co-reputation-domain`、`co-reputation/co-reputation-application` 加进 `SPRINT_MODULES`，否则覆盖率门禁对声誉模块仍然是空的。

**🟢 3. `BadgeServiceTest` 覆盖偏窄**

目前仅授予 + 幂等两条，阈值匹配由领域 `BadgePolicy` 测试兜底，可接受；后续可按徽章档位补边界用例。

### 验证方式

评审人本地复跑声誉模块单测 56 例全绿、编译通过；新增 28 例逐一核对断言与实现一致。CI 阶段 2/4 尚未跑声誉模块（见建议 2），模块覆盖以本地与全仓后续 verify 为准。

### 后续

- `SPRINT_MODULES` 随 Step3 门禁收口一并收宽，纳入声誉模块；
- 覆盖率百分比待全仓 verify 复算后回填到缺陷清单与报告；
- 建议 1 的两组口径合并版本已覆盖，无需再补。

## 三、PR #107 评审记录

| 项目 | 内容 |
| --- | --- |
| PR | [#107 feat(lab7): 接入 SonarQube 质量门禁与 JaCoCo 覆盖率报告](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/107) |
| 作者 | 王钰滢（`wtr999-jpg`） |
| 评审人 | 邹园园（`zouyuanyuan27`）、张嘉雨（`ember216`） |
| 评审日期 | 2026-11-28 |
| 变更规模 | 4 个文件：`.github/workflows/ci.yml`（+26）、`backend/pom.xml`（+21）、`backend/sonar-project.properties`（+24）、`deploy/docker-compose.sonar.yml`（+18） |
| 评审结论 | 可合并；Secrets 未配置时 Sonar 步骤自动跳过，不阻塞现有 CI 绿线；建议项在 Step3 门禁真正启用前补齐 |

### 结论

渐进式门禁与 ADR-010 决策一致：单测/覆盖率收窄 `SPRINT_MODULES`，编译与 ArchUnit 全量，集成测试 `continue-on-error`。JaCoCo HTML 归档路径与课程交付物要求对齐；job 级 env 注入规避了 `if` 不能直接引用 secrets 的限制；新增 Sonar compose 与既有配置无重复。

本地验证：父 pom `mvn -N validate` BUILD SUCCESS；`ci.yml` 与 compose YAML 语法均 OK；Sonar 插件 5.1.0.4751 为 Maven Central 真实版本。当前 Secrets 未配置，sonar 步骤自动跳过，CI 后端/前端均绿，不阻塞合并。

### 建议项（Step3 门禁启用前补齐）

**🟡 1. Sonar 扫描命令 `-N` 口径不一致**（`backend/pom.xml`、`backend/sonar-project.properties`）

注释与本地用法写 `mvn -N sonar:sonar`（非递归），CI 实际是 `mvn -f backend/pom.xml sonar:sonar`（聚合递归）。多模块项目应在聚合根一次性扫描全部子模块，`-N` 只会构建根 pom、漏掉子模块源码。建议去掉两处 `-N`，与 CI 命令统一；`inherited=false` 已保证插件不在子模块重复绑定，不需要靠 `-N` 控制。

**🟡 2. `SONAR_HOST_URL` 未纳入跳过条件**（`.github/workflows/ci.yml` Sonar 步骤）

`if: ${{ env.SONAR_TOKEN != '' }}` 只判 token；若配了 token 漏配 host，会回退到 pom 默认 `localhost:9000`，在 runner 上扫描失败。建议改成 token 与 host 都非空才执行。

**🟡 3. 基线 3 个 Bugs 未登记处置**

门禁规则 Bugs >0 失败，当前基线 3 个 Bugs；Secrets 配上后 `qualitygate.wait=true` 会直接拦红。PR 未列出这 3 个 Bugs 的位置与责任人。建议在 defect-list 登记并明确本 PR 修还是排后续（对应 #110 修复），否则门禁长期 ERROR。

**🟡 4. Sonar 覆盖率口径与 verify 范围不一致**

CI 扫描前 verify 只跑 `SPRINT_MODULES`，而 `sonar.sources` 列全部 6 个模块，未跑模块无 jacoco.xml，覆盖率会被低估/记 0，与本地全量基线不可比。启用前统一为全量 verify，或限定 Sonar 扫描范围。

**🟢 5. 门禁阈值不在仓库可核验**：70% 覆盖率、Bugs/Vuln 条件配在 SonarQube 服务端 Quality Gate，代码内看不到。建议在 PR 或 ADR 记录 gate 定义，评审会现场展示。

**🟢 6. 范围与关联**：`SPRINT_MODULES` 当前近似 S1 范围，按 Sprint 范围规划应删变量恢复全量，随 Sprint 及时收宽；PR 补 `Closes #` 关联 Issue。

### 验证方式

本地父 pom `mvn -N validate` BUILD SUCCESS；`ci.yml` 与 `docker-compose.sonar.yml` YAML 语法核对通过；Sonar Maven 插件版本 5.1.0.4751 在 Maven Central 可查；渐进门禁口径与 ADR-010 逐条对照一致。Sonar 实际扫描待仓库 Secrets（`SONAR_TOKEN`、`SONAR_HOST_URL`）配置后执行。

### 后续

- 建议 1、2、4 在 Step3 配置 Secrets 前先改齐；
- 3 个 Bugs 由 #110 修复并复核质量门禁结论；
- 服务端 Quality Gate 阈值定义补记到 ADR 或缺陷清单，便于评审现场核验。
