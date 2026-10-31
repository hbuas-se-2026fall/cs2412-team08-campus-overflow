# 提交物3 - Gherkin 验收标准 | 负责人: 秦悦 (QY2026932)
# 故事范围: US-006 (团队 Sprint1 P0, 团队扩展故事)
# 说明: 综合 Product Backlog 未定义图片规格，以下为团队约定约束（如需调整以团队章程为准）
# 约束: 支持 jpg/png/gif；单图 ≤ 2MB；最多 9 张
Feature: US-006 发布问题时上传图片
  作为登录用户，我希望在发布问题时上传图片，以便更好描述问题

  Scenario: 正常路径 - 上传合规图片随问题保存
    Given 用户已登录，正在发布问题
    When 用户选择一张大小合规（≤2MB）、格式为 jpg/png/gif 的图片上传并提交
    Then 图片随问题一起保存并在详情页展示

  Scenario: 边界路径 - 图片大小恰为上限 2MB
    Given 用户已登录，选择一张大小恰好 2MB 的图片
    When 用户上传该图片
    Then 图片上传成功

  Scenario: 边界路径 - 图片数量达到上限 9 张
    Given 用户已登录，为问题选择恰好 9 张合规图片
    When 用户上传并提交
    Then 全部图片保存成功

  Scenario: 异常路径 - 上传不支持的格式
    Given 用户已登录，选择 exe 等非允许格式文件
    When 用户尝试上传
    Then 系统提示“文件格式不支持”，上传失败

  Scenario: 异常路径 - 图片超过大小上限
    Given 用户已登录，选择一张大于 2MB 的图片
    When 用户尝试上传
    Then 系统提示“图片大小不能超过 2MB”，上传失败

  Scenario: 异常路径 - 未登录上传
    Given 用户未登录
    When 用户尝试上传图片
    Then 系统跳转登录页面，无法上传
