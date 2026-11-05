# Features 目录

按业务域组织的 feature 模块，每个 feature 内部自包含组件、hooks、store、API 调用。

```
features/
  qa/            问答域（问题列表/详情/提问/回答/投票/评论）
  identity/      身份域（登录/注册/个人资料/管理员）
  reputation/    声誉域（排行榜/徽章/悬赏）
  discovery/     发现域（搜索/通知/相关推荐）
  common/        跨 feature 复用的组件、hooks、工具
```

约定：feature 之间不直接 import 对方的内部实现，通过 `src/api/` 的类型和 props 通信。
