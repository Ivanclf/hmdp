package com.hmdp.config;

import com.hmdp.utils.RedisProps;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RedissonConfig {
    private final RedisProps redisProps;
    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        String address = String.format("redis://%s:%d", redisProps.getHost(), redisProps.getPort());
        config.useSingleServer().setAddress(address).setPassword(redisProps.getPassword());
        // 启动redissonClient对象
        return Redisson.create(config);
    }
}
