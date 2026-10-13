# 音乐会售票数据表与实体：实施计划

> **For implementation:** Execute this plan inline; do not add services, controllers, mapper methods, or order/payment/inventory workflows.

**Goal:** 在现有 Spring Boot/MyBatis-Plus 项目中加入音乐会自营售票 MVP 的建表 SQL 和领域实体映射。

**Architecture:** 保留现有 `tb_user`，以十张新增表承载艺人、城市、场地、演出、场次、票档、订单、订单明细、支付与电子票。建表脚本不使用数据库外键，而为逻辑关联字段建立查询索引；实体与现有 `User` 一样使用 MyBatis-Plus 注解和自动填充的创建/更新时间。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus 3.5、Lombok、MySQL 8 DDL。

设计依据：[2026-10-13-concert-ticket-schema-design.md](../specs/2026-10-13-concert-ticket-schema-design.md)

---

### Task 1：新增 MyBatis-Plus 领域实体

**Files:**
- Create: `src/main/java/com/heima/sobdianping/entity/Artist.java`
- Create: `src/main/java/com/heima/sobdianping/entity/City.java`
- Create: `src/main/java/com/heima/sobdianping/entity/Venue.java`
- Create: `src/main/java/com/heima/sobdianping/entity/Concert.java`
- Create: `src/main/java/com/heima/sobdianping/entity/ConcertSession.java`
- Create: `src/main/java/com/heima/sobdianping/entity/SessionTicketTier.java`
- Create: `src/main/java/com/heima/sobdianping/entity/TicketOrder.java`
- Create: `src/main/java/com/heima/sobdianping/entity/TicketOrderItem.java`
- Create: `src/main/java/com/heima/sobdianping/entity/Payment.java`
- Create: `src/main/java/com/heima/sobdianping/entity/Ticket.java`

**Step 1: Implement minimal code**

为每张新表建立一个只含字段、`@TableName`、`@TableId(type = IdType.AUTO)` 和审计字段填充注解的实体；金额、库存和排序字段使用 `Integer`，时间使用 `LocalDateTime`，状态使用 `Integer`。

**Step 2: Compile the application**

Run: `mvn -DskipTests compile`

Expected: PASS。

### Task 2：扩展 MySQL 初始化建表脚本

**Files:**
- Modify: `sql/schema.sql`

**Step 1: Implement minimal DDL**

在保留 `tb_user` 定义的前提下，为十张新增表补齐字段、表注释、逻辑关联索引、唯一索引和状态字段；不添加外键和流程表。

**Step 2: Validate the script structurally**

Run: PowerShell 读取 `sql/schema.sql`，确认 `tb_user` 加十张新增表、每个唯一编号索引和票档库存字段均存在。

Expected: PASS。

### Task 3：全量验证与范围检查

**Files:**
- Verify only: `sql/schema.sql`
- Verify only: `src/main/java/com/heima/sobdianping/entity/*.java`

**Step 1: Run full test suite**

Run: `mvn test`

Expected: PASS。

**Step 2: Inspect diff**

Run: `git diff --check` and `git diff -- sql/schema.sql src/main/java/com/heima/sobdianping/entity src/test/java/com/heima/sobdianping/entity`

Expected: 无空白错误；改动只覆盖 SQL、实体与设计文档。
