package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;

@SpringBootTest
public class RedisTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void isConnected() {
        Set<String> keys = stringRedisTemplate.keys("*");
        if (keys != null) {
            System.out.println(keys.size());
        }
    }
}
