# 手机验证码登录设计

## 目标

在现有 Spring Boot 4、MyBatis-Plus、MySQL 和 Redis 项目中增加手机号验证码登录能力。用户使用中国大陆 11 位手机号和 Redis 中的验证码登录；新用户自动创建，登录成功后生成 UUID Token 并保存 30 分钟登录态。

本阶段不实现 ThreadLocal、登录拦截器、Token 自动续期、退出登录或真实短信发送。

## 接口契约

### 请求

```http
POST /user/login
Content-Type: application/json
```

```json
{
  "phone": "13800138000",
  "code": "012345"
}
```

`phone` 和 `code` 均使用字符串，避免验证码前导零丢失。

### 成功响应

HTTP 状态为 `200 OK`：

```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### 失败响应

手机号格式错误时返回 `400 Bad Request`：

```json
{
  "success": false,
  "message": "手机号格式错误",
  "data": null
}
```

验证码不存在、不匹配或已过期时返回 `401 Unauthorized`：

```json
{
  "success": false,
  "message": "验证码错误或已过期",
  "data": null
}
```

未知异常返回 `500 Internal Server Error` 和通用错误信息，不向客户端暴露数据库、Redis 或堆栈细节。

## 用户表

目标数据库固定为本机 `127.0.0.1:3306/sobdianping`。表名为 `tb_user`：

```sql
CREATE TABLE tb_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    gender TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '性别：0未知，1男，2女',
    phone VARCHAR(11) NOT NULL COMMENT '手机号',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

手机号唯一索引用于保证同一手机号只有一个用户，并防止并发首次登录创建重复记录。

`User` 实体字段为：

- `Long id`
- `String username`
- `Integer gender`
- `String phone`
- `LocalDateTime createTime`
- `LocalDateTime updateTime`

新用户默认用户名为 `user_` 加 10 位随机字母数字字符，默认性别为 `0`。创建时间和修改时间由 MyBatis-Plus `MetaObjectHandler` 自动填充。

## Redis 设计

### 验证码

复用现有 Key：

```text
login:code:+86{phone}
```

验证码 TTL 保持 60 秒。登录失败不删除验证码；登录成功且 Token 保存完成后删除验证码，确保验证码只能成功使用一次，同时避免 Token 写入失败时验证码提前失效。

### 登录态

```text
Key: login:token:{uuid}
Type: Hash
TTL: 30 分钟
```

Hash 只包含：

```text
id
phone
```

Token 使用带连字符的标准 UUID。暂不自动刷新 TTL。客户端后续通过以下请求头传递原始 UUID，不添加 `Bearer` 前缀：

```http
Authorization: 550e8400-e29b-41d4-a716-446655440000
```

本阶段不读取该请求头，也不将用户保存到 ThreadLocal；这部分留给后续登录拦截器实现。

## 登录流程

1. Controller 接收 `LoginDTO`，字段为 `phone` 和 `code`。
2. Service 使用 `^1[3-9]\\d{9}$` 校验手机号，失败时抛出 HTTP 400 业务异常。
3. Service 从 `login:code:+86{phone}` 读取验证码。
4. 验证码不存在或与请求值不一致时抛出 HTTP 401 业务异常，不删除验证码。
5. 根据手机号查询 `tb_user`。
6. 用户不存在时，创建默认用户名和默认性别的用户。
7. 并发插入若触发手机号唯一键冲突，重新根据手机号查询已创建的用户。
8. 生成 UUID Token。
9. 将用户 `id` 和 `phone` 写入 Redis Hash，并设置 30 分钟 TTL。
10. Token 保存成功后删除验证码。
11. 返回包含 Token 的统一成功响应。

数据库用户创建在本地事务内完成。MySQL 和 Redis 不使用分布式事务：如果新用户已经创建但 Token 写入失败，用户记录保留，接口返回系统错误，验证码也保留以便重试。

## 代码结构

```text
common/
  Result.java

config/
  MyBatisMetaObjectHandler.java

controller/
  UserController.java

dto/
  LoginDTO.java

vo/
  LoginVO.java

entity/
  User.java

exception/
  BusinessException.java
  GlobalExceptionHandler.java

service/
  UserService.java
  impl/UserServiceImpl.java

sql/
  schema.sql
```

`UserController` 只处理 HTTP 输入输出。`UserServiceImpl` 负责校验、查询或创建用户、生成 Token 和操作 Redis。`BusinessException` 携带 HTTP 状态与公开错误信息，`GlobalExceptionHandler` 统一输出 `Result<T>`。

## 测试策略

### 单元测试

- 手机号格式错误时拒绝登录。
- 验证码不存在或不一致时拒绝登录。
- 已有用户登录时不重复创建用户。
- 新用户使用默认用户名、默认性别和手机号创建。
- Token 是合法 UUID。
- Redis Hash 只写入 `id`、`phone`。
- Token TTL 为 30 分钟。
- 成功登录后删除验证码。
- 登录失败或 Token 写入失败时不删除验证码。

### Controller 与异常处理测试

- 登录成功返回 HTTP 200 和统一成功响应。
- 手机号错误返回 HTTP 400。
- 验证码错误或过期返回 HTTP 401。
- 响应中不包含验证码或内部异常细节。

### 集成测试

- MyBatis-Plus 能查询和创建 `tb_user`。
- Redis 能保存 Hash 登录态并应用 TTL。
- Spring 上下文能加载 Mapper、异常处理器和时间自动填充组件。

### 冒烟测试

应用启动后，使用本地 MySQL 和 Redis 快速验证核心链路：准备验证码、调用登录接口、检查 HTTP 响应、确认用户记录和 Redis Token 存在，并确认验证码已删除。

## 实施约束

- 使用 Java 17、Spring Boot 4.1.1、MyBatis-Plus 3.5.15。
- 所有新增业务代码遵循现有 Controller、Service、Mapper 分层以及阿里 Java 命名规范。
- 先写失败测试，再实现生产代码。
- 执行 DDL 前再次确认目标为 `127.0.0.1:3306/sobdianping`，不得修改其他数据库或已有表。
- 保留工作区中用户已有且与本功能无关的修改。
