package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result shopTypeList() {
        String key = "shopType";
        List<ShopType> typeList = new ArrayList<>();
        // 检验是否在redis中存有数据
        String string;
        while (stringRedisTemplate.opsForList().size(key) > 0 && StrUtil.isNotBlank(string = stringRedisTemplate.opsForList().leftPop(key))) {
            ShopType shopType = JSONUtil.toBean(string, ShopType.class);
            typeList.add(shopType);
        }
        // 存在则直接返回
        if (!typeList.isEmpty()) {
            return Result.ok(typeList);
        }
        // 不存在则到sql中读取数据并写回redis
        typeList = query().orderByAsc("sort").list();
        List<String> typeListRedis = new ArrayList<>();
        for(ShopType st : typeList) {
            typeListRedis.add(JSONUtil.toJsonStr(st));
        }
        stringRedisTemplate.opsForList().rightPushAll(key, typeListRedis);
        return Result.ok(typeList);
    }
}
