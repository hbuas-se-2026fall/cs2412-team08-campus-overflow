# PR #107 评审记录

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| PR | [#107 feat(lab7): 接入 SonarQube 质量门禁与 JaCoCo 覆盖率报告](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/107) |
| 作者 | 王钰滢（`wtr999-jpg`） |
| 评审人 | 邹园园（`zouyuanyuan27`）、张嘉雨（`ember216`） |
| 评审日期 | 2026-11-28 |
| 变更规模 | 4 个文件：`.github/workflows/ci.yml`（+26）、`backend/pom.xml`（+21）、`backend/sonar-project.properties`（+24）、`deploy/docker-compose.sonar.yml`（+18） |
| 评审结论 | 可合并；Secrets 未配置时 Sonar 步骤自动跳过，不阻塞现有 CI 绿线；建议项在 Step3 门禁真正启用前补齐 |

## 一、结论

渐进式门禁与 ADR-010 决策一致：单测/覆盖率收窄 `SPRINT_MODULES`，编译与 ArchUnit 全量，集成测试 `continue-on-error`。JaCoCo HTML 归档路径与指导书 9.4 交付物对齐；job 级 env 注入规避了 `if` 不能直接引用 secrets 的限制；新增 Sonar compose 与既有配置无重复。

本地验证：父 pom `mvn -N validate` BUILD SUCCESS；`ci.yml` 与 compose YAML 语法均 OK；Sonar 插件 5.1.0.4751 为 Maven Central 真实版本。当前 Secrets 未配置，sonar 步骤自动跳过，CI 后端/前端均绿，不阻塞合并。

## 二、建议项（Step3 门禁启用前补齐）

### 🟡 建议改

**1. Sonar 扫描命令 `-N` 口径不一致**（`backend/pom.xml`、`backend/sonar-project.properties`）

注释与本地用法写 `mvn -N sonar:sonar`（非递归），CI 实际是 `mvn -f backend/pom.xml sonar:sonar`（聚合递归）。多模块项目应在聚合根一次性扫描全部子模块，`-N` 只会构建根 pom、漏掉子模块源码。建议去掉两处 `-N`，与 CI 命令统一；`inherited=false` 已保证插件不在子模块重复绑定，不需要靠 `-N` 控制。

**2. `SONAR_HOST_URL` 未纳入跳过条件**（`.github/workflows/ci.yml` Sonar 步骤）

`if: ${{ env.SONAR_TOKEN != '' }}` 只判 token；若配了 token 漏配 host，会回退到 pom 默认 `localhost:9000`，在 runner 上扫描失败。建议改成 token 与 host 都非空才执行。

**3. 基线 3 个 Bugs 未登记处置**

门禁规则 Bugs >0 失败，当前基线 3 个 Bugs；Secrets 配上后 `qualitygate.wait=true` 会直接拦红。PR 未列出这 3 个 Bugs 的位置与责任人。建议在 defect-list 登记并明确本 PR 修还是排后续（对应 #110 修复），否则门禁长期 ERROR。

**4. Sonar 覆盖率口径与 verify 范围不一致**

CI 扫描前 verify 只跑 `SPRINT_MODULES`，而 `sonar.sources` 列全部 6 个模块，未跑模块无 jacoco.xml，覆盖率会被低估/记 0，与本地全量基线不可比。启用前统一为全量 verify，或限定 Sonar 扫描范围。

### 🟢 可选

**5. 门禁阈值不在仓库可核验**：70% 覆盖率、Bugs/Vuln 条件配在 SonarQube 服务端 Quality Gate，代码内看不到。建议在 PR 或 ADR 记录 gate 定义，评审会现场展示。

**6. 范围与关联**：`SPRINT_MODULES` 当前近似 S1 范围，按指导书 8.3 表 S3 应删变量恢复全量，随 Sprint 及时收宽；PR 补 `Closes #` 关联 Issue。

## 三、验证方式

本地父 pom `mvn -N validate` BUILD SUCCESS；`ci.yml` 与 `docker-compose.sonar.yml` YAML 语法核对通过；Sonar Maven 插件版本 5.1.0.4751 在 Maven Central 可查；渐进门禁口径与 ADR-010 逐条对照一致。Sonar 实际扫描待仓库 Secrets（`SONAR_TOKEN`、`SONAR_HOST_URL`）配置后执行。

## 四、后续

- 建议 1、2、4 在 Step3 配置 Secrets 前先改齐；
- 3 个 Bugs 由 #110 修复并复核质量门禁结论；
- 服务端 Quality Gate 阈值定义补记到 ADR 或缺陷清单，便于评审现场核验。
