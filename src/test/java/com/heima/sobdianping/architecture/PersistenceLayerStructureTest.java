package com.heima.sobdianping.architecture;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceLayerStructureTest {

    @Test
    void userMapperShouldUseMyBatisPlusBaseMapper() {
        assertTrue(BaseMapper.class.isAssignableFrom(UserMapper.class));
    }

    @Test
    void userServiceImplementationShouldUseMyBatisPlusServiceImpl() {
        assertTrue(ServiceImpl.class.isAssignableFrom(UserServiceImpl.class));
    }
}
