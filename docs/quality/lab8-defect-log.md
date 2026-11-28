# Lab8 缺陷登记表

配套文档：`regression-plan.md`（定级标准与流转规则）、`regression-checklist.md`（回归用例）。编号接续 Lab7 的 D-01 至 D-08，本表从 D-09 起。

台账归属：本表是 D-09 起的唯一台账，新增缺陷只在此登记。`defect-list.md` 中的 D-01 至 D-08 作为 Lab7 历史归档保留，不再新增编号，也不在本表重复登记；同一缺陷以编号更早的条目为事实源，状态变更在归档表中更新后，在本表摘要里同步一行。

## 一、字段定义

| 字段 | 说明 |
| --- | --- |
| 编号 | D-XX，递增，不复用 |
| 发现时间 | 精确到日，执行类缺陷精确到时分 |
| 发现人 | 实际执行者 |
| 阶段 | 静态核对 / 部署 / 冒烟 / 回归 / 稳定性观察 |
| 环境 | 线上域名 / 测试服务器 / 本地 / 仅代码与文档 |
| 严重级 | P0 至 P3，标准见 `regression-plan.md` |
| 模块 | 具体到 Maven 模块或文档路径 |
| 标题 | 一句话，含主客体与现象 |
| 复现步骤 | 命令或操作序列，可照做 |
| 期望 / 实际 | 对照写，不合并 |
| 影响 | 对上线、判定、交接的具体后果 |
| 证据 | 响应原文、日志片段、截图路径或核对脚本输出 |
| 责任人 | 修复人，初判后分派 |
| 状态 | 新建 / 已确认 / 修复中 / 待复验 / 已关闭 / 不修 |
| 关联用例 | 对应 R-XX |
| 关闭说明 | 复验方式与结果，不修时写理由 |

## 二、状态流转

新建登记后 24 小时内完成定级与分派。责任人认领后置「已确认」，提交修复后置「待复验」，本人用关联用例复跑通过后置「已关闭」。判定不修的必须写明理由并经该模块分工负责人确认，状态终态为「不修」。

## 三、已登记缺陷

### D-09 错误码文档只收录 36 条，代码实际使用 79 条，缺 43 条（已补齐）

| 字段 | 内容 |
| --- | --- |
| 发现时间 | 2026-11-28 |
| 发现人 | 秦悦 |
| 阶段 | 静态核对 |
| 环境 | 仅代码与文档，未部署 |
| 严重级 | P1 |
| 模块 | `docs/错误码.md`、co-identity、co-qa-domain、co-bootstrap |
| 标题 | 错误码文档覆盖率不足半数，回归判定时无法查表 |
| 复现步骤 | 扫描 `backend` 各模块 `src/main` 中的错误码字面量，与 `docs/错误码.md` 内联码取差集 |
| 期望 | 文档收录全部在用错误码，或明确声明「仅列常用码，全量以源码常量类为准」 |
| 实际 | 代码 79 条，文档 36 条，缺 43 条；反向核对（文档有、代码无）为 0 条，无虚构码 |
| 影响 | 回归用例失败时无法按下发的错误码定位语义，评审与交接只能翻源码；前端提示文案无法对齐 |
| 证据 | 核对脚本输出：代码 79、文档 36，缺项按模块分组如下 |
| 责任人 | 各模块归属人补语义与触发条件，文档汇总由本人合并 |
| 状态 | 已关闭 |
| 关联用例 | R-16、R-23、R-31、R-39、R-42、R-47、R-58 至 R-62 |
| 关闭说明 | 2026-11-28 已补齐 43 条，重跑核对脚本结果代码 79 条、文档 79 条，双向差集均为空；wtr999-jpg 于 PR #108 复核确认（文档 79 / 源码 79、状态列与源码 suggestedStatus 抽查一致），据此关闭 |

缺失明细按码段分组，合计 43 条：

| 码段 | 缺失错误码 | 条数 | 主要定义位置 |
| --- | --- | --- | --- |
| identity 1000 段 | ID-1001、ID-1002、ID-1003、ID-1005、ID-1006、ID-1007、ID-1008、ID-1010、ID-1011、ID-1012、ID-1013 | 11 | `User.java`、`Course.java`、`CourseMember.java` |
| identity 2000 段 | ID-2002、ID-2003、ID-2004、ID-2005、ID-2006 | 5 | `ProfileService.java`、`UserRegistrationService.java`、`CourseService.java`、`User.java` |
| identity 3000 段 | ID-3001、ID-3002、ID-3003 | 3 | `AdminUserService.java`、`User.java`、`Course.java`、`CourseService.java` |
| identity 4000 段 | ID-4001、ID-4002、ID-4003 | 3 | `AdminUserService.java`、`ProfileService.java`、`UserQueryService.java`、`CourseService.java`、`QuestionCommandService.java` |
| 问答 1000 段 | QA-1003、QA-1004 | 2 | `Comment.java`、`TagName.java` |
| 问答 1010 段 | QA-1010、QA-1011、QA-1012、QA-1013、QA-1014、QA-1016、QA-1017、QA-1020 | 8 | `Question.java`、`Answer.java`、`Vote.java`、`Comment.java` |
| 问答 2000 段 | QA-2003 | 1 | `Answer.java` |
| 问答 3000 段 | QA-3002、QA-3003、QA-3004 | 3 | `Answer.java`、`Question.java`、`AnswerCommandService.java` |
| 问答 4000 段 | QA-4001、QA-4002、QA-4003 | 3 | `QuestionCommandService.java`、`AnswerCommandService.java`、`CommentService.java`、`VoteService.java` |
| 认证 | AUTH-403 | 1 | `GlobalExceptionHandler.java`、`SecurityConfig.java` |
| 系统 | SYS-400、SYS-400-VALIDATION、SYS-500 | 3 | `GlobalExceptionHandler.java` |
| 合计 |  | 43 |  |

### D-11 Sonar 配置有两个事实源，注释与 CI 命令不一致，门禁统计范围与可测范围不对齐

| 字段 | 内容 |
| --- | --- |
| 发现时间 | 2026-11-28 |
| 发现人 | 秦悦（复核人 wtr999-jpg） |
| 阶段 | 静态核对 |
| 环境 | 仅代码与文档，未部署 |
| 严重级 | P2 |
| 模块 | `backend/pom.xml`、`backend/sonar-project.properties`、`.github/workflows/ci.yml` |
| 标题 | Sonar 配置存在两份事实源，注释与 CI 实际命令不一致，覆盖率门禁的统计范围与可测范围不对齐 |
| 复现步骤 | ① 逐项对比 `backend/sonar-project.properties` 与 `backend/pom.xml` 第 41 至 52 行的 `sonar.*` 属性；② 对比 `pom.xml` 第 234 行注释与 `ci.yml` 第 78 行的实际命令；③ 本地按 CI 原命令实跑 `mvn -f backend/pom.xml sonar:sonar`，观察执行到哪些模块 |
| 期望 | Sonar 配置只有一个事实源；注释与 CI 命令一致；覆盖率门禁的统计范围与实际可测范围对齐 |
| 实际 | properties 中 11 项与 pom 属性同名同值，而 pom 第 40 行注释自陈「SonarScanner for Maven 不读 sonar-project.properties」，即该文件对 CI 与 Maven 扫描均无效；pom 注释写 `mvn -N sonar:sonar`，`ci.yml` 实际未带 `-N`；覆盖率门禁只统计 `SPRINT_MODULES` 所列模块，`co-bootstrap` 的 257 行不在统计范围 |
| 影响 | 后来人按 properties 改配置会静默失效，产生「改了没生效」的排查成本；`-N` 注释曾让评审误判为子模块重复扫描；70% 门禁的分母与实际可测行数不一致，门禁长期 Failed 时会诱导为过门禁而刷测试 |
| 证据 | properties 与 pom 的 11 项属性逐项同名同值；`ci.yml` 第 78 至 82 行命令为 `sonar:sonar -Dsonar.projectKey=campus-overflow -Dsonar.host.url -Dsonar.token -Dsonar.qualitygate.wait=true`，无 `-N`；wtr999-jpg 于 #107 评审与 #109 复核两次指出，并自行更正「重复扫描」的初判；本地实跑确认只在父工程执行一次即中断 |
| 责任人 | 王钰滢（CI 配置单人修改） |
| 状态 | 已确认 |
| 关联用例 | 无（不涉及业务流程） |
| 关闭说明 | 待关闭。收法三选一：删 properties 只留 pom 属性；或删 pom 属性只留 properties 并改用独立 scanner；或保留两份但统一措辞并在 properties 顶部注明不生效。覆盖率口径另行处理：若团队投票排除 `co-bootstrap`，同步改 `sonar.exclusions` 并写入门禁说明；若不排除，则归入补测试计划 |

## 四、跨表承接

CI 集成测试问题在 Lab7 阶段已被两条记录覆盖，本表不再新开编号，避免同一缺陷多处登记、状态互相打架：

| 编号 | 标题 | 与本表的关系 |
| --- | --- | --- |
| D-05 | 集成测试在 CI 中不计入门禁 | 事实源。`ci.yml` 阶段 5 带 `continue-on-error: true`，Testcontainers 失败不拦合并；由王钰滢 Step3 收紧门禁时关闭 |
| D-08 | 集成测试失败原因不可见 | 事实源。2026-11-28 已定位到容器启动阶段：Testcontainers 报「找不到可用的 Docker 环境」，未能拉起 `mysql:8.0.39`，两个 IT 报错（failsafe-summary errors=2、result=255）；runner 为 ubuntu-latest 且自带 Docker，具体原因是 daemon 未运行、`DOCKER_HOST` 未配置还是镜像拉取失败，待 `docker info` 诊断；详见 `defect-list.md` 的 D-08 补充说明；关闭条件为 errors 归零 |

核对说明：本表此前登记的 D-10 认为集成测试步骤未覆盖 co-bootstrap 模块，该判断有误。`ci.yml` 阶段 5 的命令是 `mvn verify -Dsurefire.skip=true`，未带 `-pl`，实际全模块执行；`SPRINT_MODULES` 只约束阶段 2 的单元测试与阶段 4 的覆盖率门禁。D-10 已撤销，编号不再保留，相关跟踪回到 D-05 与 D-08。

## 五、汇总

| 严重级 | 新建 | 已确认 | 修复中 | 待复验 | 已关闭 | 不修 | 合计 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| P1 | 0 | 0 | 0 | 0 | 1 | 0 | 1 |
| P2 | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| P3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 合计 | 0 | 1 | 0 | 0 | 1 | 0 | 2 |

回归每轮执行后更新本表，并在 `regression-checklist.md` 的执行记录中留一行。表内数字与逐条登记不一致时以逐条登记为准。
