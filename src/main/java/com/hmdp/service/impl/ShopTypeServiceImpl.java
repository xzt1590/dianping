package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
    public Result queryAllTypeList() {
        String shopListCache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_LIST_KEY);
        if(shopListCache != null) {
            List<ShopType> shopTypeList = JSONUtil.toList(shopListCache, ShopType.class);
            return Result.ok(shopTypeList, (long) shopTypeList.size());
        }
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        shopListCache = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_LIST_KEY, shopListCache);
        return Result.ok(shopTypes, (long) shopTypes.size());
    }
}
