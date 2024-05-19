package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.hash.BloomFilter;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BloomFilter<String> bloomFilter;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if(shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
//        bloomFilter.put(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        boolean isSuccess = updateById(shop);
        if(!isSuccess) {
            return Result.fail("更新店铺缓存失败");
        }
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("更新店铺缓存失败");
        }
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    private Shop queryWithMutex(Long id) {
        // 查询bloom filter
        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        if(!bloomFilter.mightContain(key)) {
//            return Result.fail("店铺不存在");
//        }

        // 查询redis
        String shopCacheStr = stringRedisTemplate.opsForValue().get(key);
        // 如果未查询到则获取互斥锁
        Shop shop;
        try {
            while(shopCacheStr == null && !tryLock(RedisConstants.LOCK_SHOP_KEY + id)) {
                Thread.sleep(50);
                shopCacheStr = stringRedisTemplate.opsForValue().get(key);
                // 重新查询到缓存数据，直接返回
                if (shopCacheStr != null) {
                    return JSONUtil.toBean(shopCacheStr, Shop.class);
                }
            }

            // 获取到了互斥锁，查询数据库
            shop = getById(id);
            shopCacheStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopCacheStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);
    private Shop queryWithLogicExpire(Long id) {
        // 查询bloom filter
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 查询redis
        String shopCacheStr = stringRedisTemplate.opsForValue().get(key);
        // 如果未查询到则返回空值
        if(StrUtil.isBlank(shopCacheStr)) {
            return null;
        }
        // 命中则判断是否过期
        RedisData redisData = JSONUtil.toBean(shopCacheStr, RedisData.class);
        LocalDateTime redisDataTime = redisData.getLocalDateTime();
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        // 未过期返回shop
        if(redisDataTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            EXECUTOR_SERVICE.submit(()->{
                try {
                    saveDataToRedis(id,30L);
                } catch (Exception e){
                    throw new RuntimeException();
                } finally {
                    unLock(lockKey);
                }
            });
        }
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key, "0", 30, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(ifAbsent);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    private void saveDataToRedis(Long id, Long expireTime) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setLocalDateTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
