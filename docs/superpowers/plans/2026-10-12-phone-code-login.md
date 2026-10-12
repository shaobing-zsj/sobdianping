# Phone Code Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement UUID-based phone verification-code login with automatic first-time user creation, a 30-minute Redis session, and uniform REST responses.

**Architecture:** `UserController` accepts transport DTOs and delegates to `UserServiceImpl`. The service validates the request, reads and atomically consumes the Redis verification code, finds or creates the MySQL user through `UserMapper`, and stores a minimal Redis Hash session. `GlobalExceptionHandler` translates typed business failures into `Result<T>` responses without leaking infrastructure details.

**Tech Stack:** Java 17, Spring Boot 4.1.1, MyBatis-Plus 3.5.15, MySQL 8, Spring Data Redis, JUnit 6/JUnit Platform, Mockito, MockMvc, Lombok

**Spec:** `docs/superpowers/specs/2026-10-12-phone-code-login-design.md`

## Global Constraints

- Use Java 17, Spring Boot 4.1.1, and MyBatis-Plus 3.5.15.
- Target only MySQL `127.0.0.1:3306/sobdianping`; never alter another database or overwrite an existing `tb_user` table.
- Follow the existing Controller, Service, Mapper layering and Alibaba Java naming conventions.
- Use strict TDD: observe each new behavior fail before adding production code.
- Preserve unrelated user changes already present in the dirty worktree.
- Store verification codes at `login:code:+86{phone}` and sessions at `login:token:{uuid}`.
- Session Hashes contain only `id` and `phone`; session TTL is exactly 30 minutes and is not refreshed.
- Do not implement ThreadLocal, authentication interceptors, logout, token refresh, or SMS delivery in this plan.

---

## File Map

| File | Responsibility |
|---|---|
| `sql/schema.sql` | Reproducible `tb_user` DDL |
| `src/main/java/com/heima/sobdianping/entity/User.java` | MyBatis-Plus user entity and fill metadata |
| `src/main/java/com/heima/sobdianping/config/MyBatisMetaObjectHandler.java` | Insert/update timestamp filling |
| `src/main/java/com/heima/sobdianping/common/Result.java` | Uniform API envelope |
| `src/main/java/com/heima/sobdianping/dto/LoginDTO.java` | Login request payload |
| `src/main/java/com/heima/sobdianping/vo/LoginVO.java` | Login response payload |
| `src/main/java/com/heima/sobdianping/exception/BusinessException.java` | Public business error plus HTTP status |
| `src/main/java/com/heima/sobdianping/exception/GlobalExceptionHandler.java` | REST exception-to-response mapping |
| `src/main/java/com/heima/sobdianping/service/UserService.java` | Login service contract |
| `src/main/java/com/heima/sobdianping/service/impl/UserServiceImpl.java` | Login orchestration, user creation, UUID and Redis session |
| `src/main/java/com/heima/sobdianping/controller/UserController.java` | `POST /user/login` endpoint |
| `src/test/java/com/heima/sobdianping/config/MyBatisMetaObjectHandlerTest.java` | Timestamp fill behavior |
| `src/test/java/com/heima/sobdianping/exception/GlobalExceptionHandlerTest.java` | Error status and body behavior |
| `src/test/java/com/heima/sobdianping/service/impl/UserServiceImplTest.java` | Login business behavior |
| `src/test/java/com/heima/sobdianping/controller/UserControllerTest.java` | Login HTTP contract |
| `src/test/java/com/heima/sobdianping/integration/LoginIntegrationTest.java` | Real MySQL and Redis integration |

---

## Execution Prerequisite: Preserve the Existing Approved Baseline

The current worktree contains the previously approved verification-code, Redis, MyBatis-Plus, Mapper, and test changes as uncommitted files. Before Task 1, verify and commit only that known baseline so every later task commit is independently buildable.

- [ ] **Step 1: Verify the existing baseline before staging it**

```powershell
mvn clean test
git status --short
```

Expected: `13` existing tests pass. The status may show only the known POM, application configuration, verification-code source, Mapper, and test changes plus this plan document.

- [ ] **Step 2: Stage only the previously approved baseline files**

```powershell
git add -- pom.xml src/main/resources/application.properties src/main/resources/application.yaml src/main/java/com/heima/sobdianping/controller/UserController.java src/main/java/com/heima/sobdianping/dto/SendCodeDTO.java src/main/java/com/heima/sobdianping/entity/User.java src/main/java/com/heima/sobdianping/mapper/UserMapper.java src/main/java/com/heima/sobdianping/service/UserService.java src/main/java/com/heima/sobdianping/service/impl/UserServiceImpl.java src/test/java/com/heima/sobdianping/architecture/PersistenceLayerStructureTest.java src/test/java/com/heima/sobdianping/controller/UserControllerTest.java src/test/java/com/heima/sobdianping/service/impl/UserServiceImplTest.java
git diff --cached --check
```

Expected: the plan document and any unrelated files remain unstaged; the cached diff has no whitespace errors.

- [ ] **Step 3: Commit the existing baseline**

```powershell
git commit -m "feat: add verification code foundation"
```

Do not use `git add .` or `git add -A` in this dirty worktree.

---

### Task 1: Create the User Schema and Complete the Persistence Model

**Files:**
- Create: `sql/schema.sql`
- Modify: `src/main/java/com/heima/sobdianping/entity/User.java`
- Create: `src/main/java/com/heima/sobdianping/config/MyBatisMetaObjectHandler.java`
- Create: `src/test/java/com/heima/sobdianping/config/MyBatisMetaObjectHandlerTest.java`

**Interfaces:**
- Consumes: existing `UserMapper extends BaseMapper<User>` and configured `sobdianping` datasource.
- Produces: `tb_user`; `User` fields `id`, `username`, `gender`, `phone`, `createTime`, `updateTime`; timestamp fill handler.

- [ ] **Step 1: Write the schema file without executing it**

Create `sql/schema.sql`:

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

- [ ] **Step 2: Write a failing timestamp-fill test**

Create `MyBatisMetaObjectHandlerTest.java`:

```java
package com.heima.sobdianping.config;

import com.heima.sobdianping.entity.User;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisMetaObjectHandlerTest {

    @Test
    void shouldFillCreateAndUpdateTimeOnInsert() {
        User user = new User();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        new MyBatisMetaObjectHandler().insertFill(metaObject);

        assertNotNull(user.getCreateTime());
        assertNotNull(user.getUpdateTime());
    }

    @Test
    void shouldReplaceUpdateTimeOnUpdate() {
        User user = new User();
        LocalDateTime oldUpdateTime = LocalDateTime.of(2025, 1, 1, 0, 0);
        user.setUpdateTime(oldUpdateTime);
        MetaObject metaObject = SystemMetaObject.forObject(user);

        new MyBatisMetaObjectHandler().updateFill(metaObject);

        assertTrue(user.getUpdateTime().isAfter(oldUpdateTime));
    }
}
```

- [ ] **Step 3: Run the test and observe RED**

Run:

```powershell
mvn "-Dtest=MyBatisMetaObjectHandlerTest" test
```

Expected: test compilation fails because `MyBatisMetaObjectHandler` and the time properties do not exist.

- [ ] **Step 4: Extend the entity with exact MyBatis-Plus metadata**

Replace `User.java` with:

```java
package com.heima.sobdianping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private Integer gender;
    private String phone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

- [ ] **Step 5: Implement timestamp filling**

Create `MyBatisMetaObjectHandler.java`:

```java
package com.heima.sobdianping.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        setFieldValByName("createTime", now, metaObject);
        setFieldValByName("updateTime", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
```

- [ ] **Step 6: Run the focused test and observe GREEN**

Run:

```powershell
mvn "-Dtest=MyBatisMetaObjectHandlerTest" test
```

Expected: `2` tests pass.

- [ ] **Step 7: Verify the exact database target and absence of the table**

Run this read-only command before any DDL:

```powershell
& 'D:\Mysql\MySQL Server 8.0\bin\mysql.exe' --user=root --password=1234 --host=127.0.0.1 --port=3306 --database=sobdianping --execute="SELECT DATABASE(), @@hostname, @@port; SHOW TABLES LIKE 'tb_user';"
```

Expected: database is `sobdianping`, port is `3306`, and no `tb_user` row is returned. If `tb_user` already exists, stop and inspect `SHOW CREATE TABLE tb_user`; do not overwrite it.

- [ ] **Step 8: Create and verify the table**

Run:

```powershell
Get-Content -Raw 'sql\schema.sql' | & 'D:\Mysql\MySQL Server 8.0\bin\mysql.exe' --user=root --password=1234 --host=127.0.0.1 --port=3306 --database=sobdianping
& 'D:\Mysql\MySQL Server 8.0\bin\mysql.exe' --user=root --password=1234 --host=127.0.0.1 --port=3306 --database=sobdianping --execute="SHOW CREATE TABLE tb_user;"
```

Expected: columns and unique key `uk_phone` exactly match `sql/schema.sql`.

- [ ] **Step 9: Commit only Task 1 files**

```powershell
git add -- sql/schema.sql src/main/java/com/heima/sobdianping/entity/User.java src/main/java/com/heima/sobdianping/config/MyBatisMetaObjectHandler.java src/test/java/com/heima/sobdianping/config/MyBatisMetaObjectHandlerTest.java
git commit -m "feat: add user persistence model"
```

---

### Task 2: Add Login Contracts and Uniform Error Responses

**Files:**
- Create: `src/main/java/com/heima/sobdianping/common/Result.java`
- Create: `src/main/java/com/heima/sobdianping/dto/LoginDTO.java`
- Create: `src/main/java/com/heima/sobdianping/vo/LoginVO.java`
- Create: `src/main/java/com/heima/sobdianping/exception/BusinessException.java`
- Create: `src/main/java/com/heima/sobdianping/exception/GlobalExceptionHandler.java`
- Create: `src/test/java/com/heima/sobdianping/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Spring MVC `HttpStatus`, `ResponseEntity`, and Jackson serialization.
- Produces: `Result<T>`, `LoginDTO`, `LoginVO`, `BusinessException(HttpStatus, String)`, global 4xx/5xx mapping.

- [ ] **Step 1: Write failing exception-response tests**

Create `GlobalExceptionHandlerTest.java`:

```java
package com.heima.sobdianping.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRenderBusinessStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("验证码错误或已过期"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password must not leak");
        }
    }
}
```

- [ ] **Step 2: Run the test and observe RED**

Run:

```powershell
mvn "-Dtest=GlobalExceptionHandlerTest" test
```

Expected: compilation fails because the response and exception classes do not exist.

- [ ] **Step 3: Implement request and response DTOs**

Create `LoginDTO.java`:

```java
package com.heima.sobdianping.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String phone;
    private String code;
}
```

Create `LoginVO.java`:

```java
package com.heima.sobdianping.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
}
```

- [ ] **Step 4: Implement the uniform response envelope**

Create `Result.java`:

```java
package com.heima.sobdianping.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> {

    private boolean success;
    private String message;
    private T data;

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(true, message, data);
    }

    public static <T> Result<T> failure(String message) {
        return new Result<>(false, message, null);
    }
}
```

- [ ] **Step 5: Implement typed business and unknown-error handling**

Create `BusinessException.java`:

```java
package com.heima.sobdianping.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
```

Create `GlobalExceptionHandler.java`:

```java
package com.heima.sobdianping.exception;

import com.heima.sobdianping.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Result.failure(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure("系统繁忙，请稍后重试"));
    }
}
```

- [ ] **Step 6: Run the focused tests and observe GREEN**

Run:

```powershell
mvn "-Dtest=GlobalExceptionHandlerTest" test
```

Expected: `2` tests pass and the unexpected exception text is absent from the JSON body.

- [ ] **Step 7: Commit only Task 2 files**

```powershell
git add -- src/main/java/com/heima/sobdianping/common/Result.java src/main/java/com/heima/sobdianping/dto/LoginDTO.java src/main/java/com/heima/sobdianping/vo/LoginVO.java src/main/java/com/heima/sobdianping/exception/BusinessException.java src/main/java/com/heima/sobdianping/exception/GlobalExceptionHandler.java src/test/java/com/heima/sobdianping/exception/GlobalExceptionHandlerTest.java
git commit -m "feat: add login API contracts"
```

---

### Task 3: Implement the Login Service with Single-Use Verification Codes

**Files:**
- Modify: `src/main/java/com/heima/sobdianping/service/UserService.java`
- Modify: `src/main/java/com/heima/sobdianping/service/impl/UserServiceImpl.java`
- Modify: `src/test/java/com/heima/sobdianping/service/impl/UserServiceImplTest.java`

**Interfaces:**
- Consumes: `LoginDTO`, `LoginVO`, `BusinessException`, `UserMapper`, `StringRedisTemplate`.
- Produces: `LoginVO login(LoginDTO loginDTO)`; Redis `login:token:{uuid}` Hash with `id` and `phone`; atomic verification-code consumption after session persistence.

- [ ] **Step 1: Add failing validation and verification-code tests**

Extend `UserServiceImplTest` with mocked `UserMapper`, `HashOperations<String, Object, Object>`, and a fixed UUID supplier. The setup and first tests must be:

```java
private UserMapper userMapper;
private HashOperations<String, Object, Object> hashOperations;
private Supplier<UUID> uuidSupplier;

@BeforeEach
@SuppressWarnings("unchecked")
void setUp() {
    stringRedisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    hashOperations = mock(HashOperations.class);
    secureRandom = mock(SecureRandom.class);
    userMapper = mock(UserMapper.class);
    uuidSupplier = mock(Supplier.class);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    userService = new UserServiceImpl(stringRedisTemplate, userMapper, secureRandom, uuidSupplier);
}

@Test
void shouldRejectInvalidLoginPhone() {
    LoginDTO loginDTO = login("12800138000", "123456");

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> userService.login(loginDTO)
    );

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    assertEquals("手机号格式错误", exception.getMessage());
    verify(stringRedisTemplate, never()).opsForValue();
}

@Test
void shouldRejectMissingOrDifferentVerificationCodeWithoutDeletingIt() {
    LoginDTO loginDTO = login("13800138000", "123456");
    when(valueOperations.get("login:code:+8613800138000")).thenReturn("654321");

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> userService.login(loginDTO)
    );

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    verify(stringRedisTemplate, never()).delete("login:code:+8613800138000");
    verifyNoInteractions(userMapper);
}
```

Add this helper at the bottom of the test class:

```java
private LoginDTO login(String phone, String code) {
    LoginDTO loginDTO = new LoginDTO();
    loginDTO.setPhone(phone);
    loginDTO.setCode(code);
    return loginDTO;
}
```

- [ ] **Step 2: Add failing existing-user success tests**

Add a test with a fixed UUID and an existing `User`:

```java
@Test
void shouldLoginExistingUserAndStoreMinimalSession() {
    LoginDTO loginDTO = login("13800138000", "123456");
    User user = new User();
    user.setId(1001L);
    user.setPhone("13800138000");
    UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    when(valueOperations.get("login:code:+8613800138000")).thenReturn("123456");
    when(userMapper.selectOne(any())).thenReturn(user);
    when(uuidSupplier.get()).thenReturn(uuid);
    when(stringRedisTemplate.expire(
            "login:token:" + uuid,
            Duration.ofMinutes(30)
    )).thenReturn(true);
    when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

    LoginVO result = userService.login(loginDTO);

    assertEquals(uuid.toString(), result.getToken());
    verify(hashOperations).putAll(
            "login:token:" + uuid,
            Map.of("id", "1001", "phone", "13800138000")
    );
    verify(stringRedisTemplate).expire(
            "login:token:" + uuid,
            Duration.ofMinutes(30)
    );
    verify(userMapper, never()).insert(any());
}
```

- [ ] **Step 3: Add failing new-user and failure-ordering tests**

Add tests that capture the inserted user and preserve the code on Redis failure:

```java
@Test
void shouldCreateNewUserWithDefaultsBeforeLogin() {
    LoginDTO loginDTO = login("13900139000", "123456");
    UUID uuid = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    when(valueOperations.get("login:code:+8613900139000")).thenReturn("123456");
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userMapper.insert(any())).thenAnswer(invocation -> {
        User inserted = invocation.getArgument(0);
        inserted.setId(2002L);
        return 1;
    });
    when(uuidSupplier.get()).thenReturn(uuid);
    when(stringRedisTemplate.expire(anyString(), eq(Duration.ofMinutes(30))))
            .thenReturn(true);
    when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

    userService.login(loginDTO);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).insert(captor.capture());
    assertEquals("13900139000", captor.getValue().getPhone());
    assertEquals(0, captor.getValue().getGender());
    assertTrue(captor.getValue().getUsername().matches("user_[A-Za-z0-9]{10}"));
}

@Test
void shouldKeepVerificationCodeWhenSessionWriteFails() {
    LoginDTO loginDTO = login("13800138000", "123456");
    User user = new User();
    user.setId(1001L);
    user.setPhone("13800138000");
    when(valueOperations.get("login:code:+8613800138000")).thenReturn("123456");
    when(userMapper.selectOne(any())).thenReturn(user);
    when(uuidSupplier.get()).thenReturn(UUID.randomUUID());
    doThrow(new IllegalStateException("write failed"))
            .when(hashOperations).putAll(anyString(), anyMap());

    assertThrows(IllegalStateException.class, () -> userService.login(loginDTO));

    verify(stringRedisTemplate, never()).execute(any(), anyList(), anyString());
}
```

Add the unique-key race test:

```java
@Test
void shouldReuseConcurrentUserWhenUniquePhoneInsertLosesRace() {
    LoginDTO loginDTO = login("13700137000", "123456");
    User concurrentUser = new User();
    concurrentUser.setId(3003L);
    concurrentUser.setPhone("13700137000");
    UUID uuid = UUID.fromString("9c858901-8a57-4791-81fe-4c455b099bc9");
    when(valueOperations.get("login:code:+8613700137000")).thenReturn("123456");
    when(userMapper.selectOne(any())).thenReturn(null, concurrentUser);
    when(userMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_phone"));
    when(uuidSupplier.get()).thenReturn(uuid);
    when(stringRedisTemplate.expire(anyString(), eq(Duration.ofMinutes(30))))
            .thenReturn(true);
    when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

    LoginVO result = userService.login(loginDTO);

    assertEquals(uuid.toString(), result.getToken());
    verify(hashOperations).putAll(
            "login:token:" + uuid,
            Map.of("id", "3003", "phone", "13700137000")
    );
    verify(userMapper, times(2)).selectOne(any());
}
```

- [ ] **Step 4: Run service tests and observe RED**

Run:

```powershell
mvn "-Dtest=UserServiceImplTest" test
```

Expected: compilation fails because `login(LoginDTO)` and the four-argument test constructor do not exist.

- [ ] **Step 5: Extend the service interface**

Add to `UserService.java`:

```java
LoginVO login(LoginDTO loginDTO);
```

Add imports for `LoginDTO` and `LoginVO`.

- [ ] **Step 6: Implement service dependencies and constants**

In `UserServiceImpl`, add:

```java
private static final String TOKEN_KEY_PREFIX = "login:token:";
private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
private static final String USERNAME_PREFIX = "user_";
private static final String RANDOM_CHARACTERS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
private static final int RANDOM_USERNAME_LENGTH = 10;
private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT =
        new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] "
                        + "then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class
        );

private final UserMapper userMapper;
private final Supplier<UUID> uuidSupplier;
```

Replace the constructors with:

```java
@Autowired
public UserServiceImpl(StringRedisTemplate stringRedisTemplate, UserMapper userMapper) {
    this(stringRedisTemplate, userMapper, new SecureRandom(), UUID::randomUUID);
}

UserServiceImpl(
        StringRedisTemplate stringRedisTemplate,
        UserMapper userMapper,
        SecureRandom secureRandom,
        Supplier<UUID> uuidSupplier) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.userMapper = userMapper;
    this.secureRandom = secureRandom;
    this.uuidSupplier = uuidSupplier;
}
```

- [ ] **Step 7: Implement the login flow**

Add to `UserServiceImpl`:

```java
@Override
public LoginVO login(LoginDTO loginDTO) {
    String phone = loginDTO == null ? null : loginDTO.getPhone();
    if (phone == null || !CHINA_PHONE_PATTERN.matcher(phone).matches()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式错误");
    }

    String requestCode = loginDTO.getCode();
    String codeKey = VERIFICATION_CODE_KEY_PREFIX + phone;
    String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
    if (requestCode == null || !requestCode.equals(cachedCode)) {
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
    }

    User user = findUserByPhone(phone);
    if (user == null) {
        user = createUser(phone);
    }

    String token = uuidSupplier.get().toString();
    String tokenKey = TOKEN_KEY_PREFIX + token;
    Map<String, String> session = Map.of(
            "id", user.getId().toString(),
            "phone", user.getPhone()
    );
    stringRedisTemplate.opsForHash().putAll(tokenKey, session);
    Boolean expirationSet = stringRedisTemplate.expire(tokenKey, TOKEN_TTL);
    if (!Boolean.TRUE.equals(expirationSet)) {
        stringRedisTemplate.delete(tokenKey);
        throw new IllegalStateException("Unable to set login token expiration");
    }

    Long consumed = stringRedisTemplate.execute(
            CONSUME_CODE_SCRIPT,
            List.of(codeKey),
            requestCode
    );
    if (!Long.valueOf(1L).equals(consumed)) {
        stringRedisTemplate.delete(tokenKey);
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
    }
    return new LoginVO(token);
}
```

Add helper methods:

```java
private User findUserByPhone(String phone) {
    return userMapper.selectOne(
            Wrappers.<User>lambdaQuery().eq(User::getPhone, phone)
    );
}

private User createUser(String phone) {
    User user = new User();
    user.setUsername(USERNAME_PREFIX + randomText(RANDOM_USERNAME_LENGTH));
    user.setGender(0);
    user.setPhone(phone);
    try {
        userMapper.insert(user);
        return user;
    } catch (DuplicateKeyException exception) {
        User concurrentUser = findUserByPhone(phone);
        if (concurrentUser == null) {
            throw exception;
        }
        return concurrentUser;
    }
}

private String randomText(int length) {
    StringBuilder result = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
        result.append(RANDOM_CHARACTERS.charAt(
                secureRandom.nextInt(RANDOM_CHARACTERS.length())
        ));
    }
    return result.toString();
}
```

Use imports from `java.util` (`List`, `Map`, `UUID`), `java.util.function.Supplier`, MyBatis-Plus `Wrappers`, Spring `DuplicateKeyException`, `HttpStatus`, and `DefaultRedisScript`.

- [ ] **Step 8: Run all service tests and observe GREEN**

Run:

```powershell
mvn "-Dtest=UserServiceImplTest" test
```

Expected: existing send-code tests and all new login tests pass. Verify test output contains no failures or errors.

- [ ] **Step 9: Commit only Task 3 files**

```powershell
git add -- src/main/java/com/heima/sobdianping/service/UserService.java src/main/java/com/heima/sobdianping/service/impl/UserServiceImpl.java src/test/java/com/heima/sobdianping/service/impl/UserServiceImplTest.java
git commit -m "feat: implement phone code login service"
```

---

### Task 4: Expose `POST /user/login`

**Files:**
- Modify: `src/main/java/com/heima/sobdianping/controller/UserController.java`
- Modify: `src/test/java/com/heima/sobdianping/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserService.login(LoginDTO)`, `Result.success(String, T)`, and global exception handling from Task 2.
- Produces: `POST /user/login` accepting JSON and returning HTTP 200 with `Result<LoginVO>`.

- [ ] **Step 1: Write failing success and failure HTTP tests**

Extend `UserControllerTest` setup to register `GlobalExceptionHandler`:

```java
mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
```

Add tests:

```java
@Test
void shouldReturnTokenWhenLoginSucceeds() throws Exception {
    when(userService.login(any(LoginDTO.class))).thenReturn(
            new LoginVO("550e8400-e29b-41d4-a716-446655440000")
    );

    mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"13800138000\",\"code\":\"012345\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("登录成功"))
            .andExpect(jsonPath("$.data.token")
                    .value("550e8400-e29b-41d4-a716-446655440000"))
            .andExpect(jsonPath("$.code").doesNotExist());
}

@Test
void shouldReturnBadRequestForInvalidPhone() throws Exception {
    when(userService.login(any(LoginDTO.class))).thenThrow(
            new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式错误")
    );

    mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"12800138000\",\"code\":\"123456\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("手机号格式错误"));
}

@Test
void shouldReturnUnauthorizedForInvalidCode() throws Exception {
    when(userService.login(any(LoginDTO.class))).thenThrow(
            new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期")
    );

    mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"13800138000\",\"code\":\"000000\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
}
```

- [ ] **Step 2: Run controller tests and observe RED**

Run:

```powershell
mvn "-Dtest=UserControllerTest" test
```

Expected: the login requests return 404 because `/user/login` is not implemented.

- [ ] **Step 3: Implement the login endpoint**

Add to `UserController`:

```java
@PostMapping("/login")
public ResponseEntity<Result<LoginVO>> login(@RequestBody LoginDTO loginDTO) {
    LoginVO loginVO = userService.login(loginDTO);
    return ResponseEntity.ok(Result.success("登录成功", loginVO));
}
```

Import `Result`, `LoginDTO`, and `LoginVO`. Leave the existing `POST /user/code` behavior unchanged.

- [ ] **Step 4: Run controller tests and observe GREEN**

Run:

```powershell
mvn "-Dtest=UserControllerTest" test
```

Expected: send-code tests and all three login tests pass.

- [ ] **Step 5: Commit only Task 4 files**

```powershell
git add -- src/main/java/com/heima/sobdianping/controller/UserController.java src/test/java/com/heima/sobdianping/controller/UserControllerTest.java
git commit -m "feat: expose phone code login endpoint"
```

---

### Task 5: Add Real Integration Coverage and Run the Smoke Test

**Files:**
- Create: `src/test/java/com/heima/sobdianping/integration/LoginIntegrationTest.java`
- Verify: all production and test files from Tasks 1-4

**Interfaces:**
- Consumes: real local MySQL `sobdianping`, real Redis at `127.0.0.1:6379`, and `UserService.login`.
- Produces: evidence that MyBatis-Plus persistence, Redis Hash/TTL, code consumption, and application wiring work together.

- [ ] **Step 1: Write the real integration test**

Create `LoginIntegrationTest.java`:

```java
package com.heima.sobdianping.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.service.UserService;
import com.heima.sobdianping.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class LoginIntegrationTest {

    private static final String PHONE = "19900000001";
    private static final String CODE_KEY = "login:code:+86" + PHONE;

    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldCreateUserAndPersistThirtyMinuteRedisSession() {
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE));
        stringRedisTemplate.opsForValue().set(CODE_KEY, "012345", Duration.ofSeconds(60));
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setPhone(PHONE);
        loginDTO.setCode("012345");

        LoginVO result = userService.login(loginDTO);
        String tokenKey = "login:token:" + result.getToken();

        try {
            User user = userMapper.selectOne(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            );
            assertNotNull(user);
            assertEquals(0, user.getGender());
            assertNotNull(user.getCreateTime());
            assertNotNull(user.getUpdateTime());

            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(tokenKey);
            assertEquals(user.getId().toString(), session.get("id"));
            assertEquals(PHONE, session.get("phone"));
            assertEquals(2, session.size());
            Long ttl = stringRedisTemplate.getExpire(tokenKey);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= Duration.ofMinutes(30).toSeconds());
            assertNull(stringRedisTemplate.opsForValue().get(CODE_KEY));
        } finally {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(CODE_KEY);
        }
    }
}
```

- [ ] **Step 2: Run the integration test with both services available**

Verify Redis first:

```powershell
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 ping
```

Expected: `PONG`.

Run:

```powershell
mvn "-Dtest=LoginIntegrationTest" test
```

Expected: test passes; its MySQL changes roll back and its Redis keys are removed in `finally`.

- [ ] **Step 3: Run the complete verification suite**

Run:

```powershell
mvn clean test
git diff --check
```

Expected: all tests pass, Spring context loads, and `git diff --check` reports no whitespace errors.

- [ ] **Step 4: Start the application for a real HTTP smoke test**

Start in one terminal:

```powershell
mvn spring-boot:run
```

Prepare a known verification code in another terminal:

```powershell
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 SET 'login:code:+8619900000002' '012345' EX 60
```

Call the endpoint:

```powershell
curl.exe -i -X POST "http://127.0.0.1:8080/user/login" -H "Content-Type: application/json" -d '{"phone":"19900000002","code":"012345"}'
```

Expected: HTTP 200 with `success: true`, message `登录成功`, and a UUID in `data.token`; the response contains no verification code.

- [ ] **Step 5: Inspect smoke-test side effects without printing the stored code**

Use the returned UUID as `$loginToken`, then run:

```powershell
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 HGETALL "login:token:$loginToken"
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 TTL "login:token:$loginToken"
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 EXISTS 'login:code:+8619900000002'
& 'D:\Mysql\MySQL Server 8.0\bin\mysql.exe' --user=root --password=1234 --host=127.0.0.1 --port=3306 --database=sobdianping --execute="SELECT id, username, gender, phone, create_time, update_time FROM tb_user WHERE phone='19900000002';"
```

Expected: Hash has exactly `id` and `phone`; TTL is between 1 and 1800 seconds; verification-code `EXISTS` is `0`; one user row exists with gender `0` and populated timestamps.

- [ ] **Step 6: Clean smoke-test artifacts and stop the temporary application**

Run only against the exact smoke-test key and phone:

```powershell
& 'D:\Redis\redis-cli.exe' -h 127.0.0.1 -p 6379 -a 1234 DEL "login:token:$loginToken"
& 'D:\Mysql\MySQL Server 8.0\bin\mysql.exe' --user=root --password=1234 --host=127.0.0.1 --port=3306 --database=sobdianping --execute="DELETE FROM tb_user WHERE phone='19900000002';"
```

Stop only the temporary `mvn spring-boot:run` process with `Ctrl+C`. Do not stop the user's Redis or MySQL services.

- [ ] **Step 7: Commit integration coverage**

```powershell
git add -- src/test/java/com/heima/sobdianping/integration/LoginIntegrationTest.java
git commit -m "test: cover phone login integration"
```

---

## Final Acceptance Checklist

- [ ] `tb_user` exists only in `sobdianping` and has unique key `uk_phone`.
- [ ] Invalid phone returns HTTP 400 with the uniform failure envelope.
- [ ] Missing, expired, or wrong code returns HTTP 401 without deleting the code.
- [ ] Existing users are reused; new users receive the approved default fields.
- [ ] Concurrent first login cannot create duplicate users.
- [ ] Successful login returns a UUID and never returns the verification code.
- [ ] Redis session is a Hash containing only `id` and `phone` with a 30-minute TTL.
- [ ] Verification code is consumed only after session persistence succeeds.
- [ ] ThreadLocal, interceptor, TTL refresh, logout, and SMS delivery remain out of scope.
- [ ] Full Maven tests and the real smoke test pass with fresh evidence.
