package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在
        if(StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if(json != null) {
            return null;
        }
        // 不存在，写回
        R r = dbFallBack.apply(id);
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = CACHE_SHOP_KEY + id;
        // 查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 存在，直接返回
        if(StrUtil.isBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 若命中，则先将json反序列化为对象，判断其是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 已过期，则需要缓存重建，获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock) {
            // Double Check
            json = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isBlank(json)) {
                return null;
            }
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())) {
                return r;
            }

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存和释放锁
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
