# 缺陷清单（Sprint 4 / Lab8）

登记规则：任何人发现问题即登记，编号不复用；优先级按影响面与演示阻断程度定级，P0 立即修，P1 当班修，P2 排入下个迭代。
分级口径：P0 阻断主流程或数据错误；P1 功能可用但有明显错误或体验断裂；P2 文案、样式、可维护性问题。

## 一、当前已登记

| 编号 | 标题 | 模块 | 现象与复现 | 优先级 | 状态 | 来源 |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | 声誉页首屏闪现「暂无流水记录」 | frontend / 声誉档案 | 进入 `/users/{id}` 后，流水请求未返回前 `entries` 为空且 `ledgerNote` 为空，先渲染空态提示再切表格 | P2 | 待修 | 评审 #98 |
| D-02 | 流水页分页大小存在两处常量 | frontend / 声誉档案 | `api.ledger(userId, page)` 依赖 `api.ts` 默认 `size = 20`，组件内另有 `PAGE_SIZE = 20`，改一处漏一处会导致分页与总数不一致 | P2 | 待修 | 评审 #98 |
| D-03 | 演示脚本 4:20 段的 Swagger 分组名错误 | docs / 演示材料 | `GET /api/v1/admin/reputation/reconciliation` 位于 `AdminReputationController`，`@Tag` 为「管理 Admin」，脚本写成在「声誉 Reputation」分组操作，现场会找不到入口 | P1 | 已合并，待补改 | 评审 #99 |
| D-04 | 前端缺少自动化测试 | frontend | CI 仅执行 `typecheck` 与 `build`，无单测与组件测试，页面逻辑全靠人工冒烟 | P1 | 待排期 | 自查 |
| D-05 | 集成测试在 CI 中不计入门禁 | CI / backend | `ci.yml` 的集成测试步骤带 `continue-on-error: true`，Testcontainers 失败不会拦住合并 | P1 | 待修（王钰滢 Step3） | 自查 |
| D-06 | 覆盖率门禁阈值未启用 | CI / backend | 根 pom 的 `domain.coverage.minimum` 为 0 且未配置 `check` 执行，JaCoCo 只出报告不拦截 | P1 | 待修（王钰滢 Step3） | 自查 |
| D-07 | `co-qa-interfaces` 与 `co-qa-infrastructure` 无单元测试 | backend / Q&A | 接口层 5 个类、基础设施层 17 个类零单测，此前仅靠端到端 IT 间接覆盖 | P2 | 部分缓解 | 自查 |
| D-08 | CI 集成测试步骤被 `continue-on-error` 掩盖，失败原因不可见 | CI / backend | 「集成测试」步骤在 Check 注释里带 `Process completed with exit code 1`，后端 job 仍显示 succeeded，API 里该 step 结论为 success（continue-on-error 的表现）。e4973b2 的运行 run 45740544391 中该步骤耗时 28 秒；本地用同一条命令 `verify -Dsurefire.skip=true` 执行时，failsafe 能识别 `CampusOverflowFlowIT` 与 `QuestionInteractionIT`，说明 CI 该步骤不是空跑，失败发生在运行期。当前拿不到步骤日志正文（匿名 API 403、Actions 日志面板未渲染、artifact 下载被拦），失败原因未定位 | P1 | 待排查（王钰滢 Step3 收紧门禁时贴日志定位）；Step3 收紧前未修复则升 P0 | 自查 + 评审 #102 |

D-07 的缓解说明：新增 `QuestionInteractionIT` 覆盖投票改投、评论层级、热门标签的 Controller → Service → Repository 全链路，接口层与持久化层不再是纯裸奔状态，但仓储层的分支（软删过滤、分页边界）仍需补单测。

D-08 的影响说明：该步骤的失败结论目前无法核实（日志不可得），因此 Lab7 新增的 `QuestionInteractionIT` 与既有的 `CampusOverflowFlowIT` 都不能记为「已验证通过」，本地也无 Docker 可替代。收紧门禁前必须先拿到一次失败的步骤日志并定位根因，否则 Sprint 4 的集成测试结论不成立。

## 二、登记模板

| 字段 | 填写要求 |
| --- | --- |
| 编号 | D-序号，按登记顺序递增，不复用 |
| 标题 | 一句话说明现象，不写解决方案 |
| 模块 | identity / qa / reputation / discovery / frontend / ci / docs |
| 现象与复现 | 操作步骤 + 预期 + 实际，可附请求与响应片段 |
| 优先级 | P0 / P1 / P2，按上文口径 |
| 状态 | 待修 / 修复中 / 待验证 / 已关闭 |
| 来源 | 冒烟、评审、演示、用户反馈 |

## 三、上线后冒烟的执行约定

冒烟按 `smoke-checklist.md` 顺序执行，任一项失败即在本表登记并把失败项标为 P0 或 P1。
同一轮冒烟发现的同类问题合并登记一条，避免刷条目。
