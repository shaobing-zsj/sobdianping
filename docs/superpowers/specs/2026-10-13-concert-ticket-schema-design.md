# 音乐会自营售票 MVP：数据表设计

## 目标

为只销售音乐会门票的自营售票 MVP 提供可覆盖“发现演出 → 选择场次和票档 → 创建订单 → 支付 → 发放电子票”的关系型数据模型。

## 已确认范围

- 一场演出仅关联一位艺人；不处理音乐节、拼盘演出或多艺人联合演出。
- 采用票档库存，不做选座，也不生成独立库存预占表。
- 待支付订单所占库存直接由票档的 `locked_stock` 表示；后续业务流程负责超时关闭或取消时回补库存，但本次不实现该流程。
- 价格使用整数分；主键使用 `BIGINT UNSIGNED`；时间使用 `DATETIME`；状态使用 `TINYINT UNSIGNED`。
- 不加数据库外键，使用逻辑关联字段和索引，与现有 `tb_user` 表的风格保持一致，并避免级联删除、迁移顺序和线上运维耦合。

## 表体系

| 表 | 责任 |
| --- | --- |
| `tb_user` | 已有购票用户主数据，沿用。 |
| `tb_artist` | 艺人资料。 |
| `tb_city` | 可售城市。 |
| `tb_venue` | 场地及其所属城市。 |
| `tb_concert` | 演出内容页；属于一位艺人。 |
| `tb_concert_session` | 具体场次、演出和开售时间；关联场地与演出。 |
| `tb_session_ticket_tier` | 场次的票档和聚合库存。 |
| `tb_ticket_order` | 用户订单头及订单状态、金额和关闭信息。 |
| `tb_ticket_order_item` | 订单票档明细及下单时的演出快照。 |
| `tb_payment` | 一笔订单的支付尝试/支付结果。 |
| `tb_ticket` | 按张发放的电子票和验票状态。 |

## 依赖关系

```text
tb_artist ──< tb_concert ──< tb_concert_session >── tb_venue ──> tb_city
                                    │
                                    └──< tb_session_ticket_tier

tb_user ──< tb_ticket_order ──< tb_ticket_order_item >── tb_session_ticket_tier
                   │                         │
                   └──< tb_payment           └──< tb_ticket
```

## 关键一致性约束

- 票档库存满足：`total_stock = available_stock + locked_stock + sold_stock`；该约束由后续库存扣减/回补事务维护。
- `tb_ticket_order_item` 存储标题、艺人、场地、场次、票档与价格快照，历史订单不依赖主数据更新。
- `tb_ticket` 一张记录对应一张可验票电子票，依据订单明细生成。
- 订单号、支付单号、电子票号和电子票码均全局唯一。

## 本次不包含

- 下单、库存扣减/回补、关单、支付回调、验票等业务代码。
- 多艺人、巡演、分类多对多、座位图、营销、退款和结算。
