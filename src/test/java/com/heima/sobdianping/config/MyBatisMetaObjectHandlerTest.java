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
