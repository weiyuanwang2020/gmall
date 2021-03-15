package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    /**
     * 查询全部
     * @return
     */
    @Override
    public List<SeckillGoods> findAll() {
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
        return seckillGoodsList;
    }

    /**
     * 根据ID获取实体
     * @param id
     * @return
     */
    @Override
    public SeckillGoods getSeckillGoods(Long id) {
        return (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(id.toString());
    }

    /***
     * 创建订单
     * @param skuId
     * @param userId
     */
    @Override
    public void seckillOrder(Long skuId, String userId) {
        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)){
            return;
        }

        Boolean exist = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if(!exist){
            return;
        }

        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if(StringUtils.isEmpty(goodsId)){
            redisTemplate.convertAndSend("seckillpush", skuId+":0");
            return;
        }

        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(this.getSeckillGoods(skuId));
        orderRecode.setNum(1);
        //生成订单单码
        orderRecode.setOrderStr(MD5.encrypt(userId+skuId));
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(), orderRecode);// 上面这有userId

        //更新库存
        this.updateStockCount(orderRecode.getSeckillGoods().getSkuId());
    }

    @Override
    public Result checkOrder(Long skuId, String userId) {
        Boolean isExist = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if(isExist){
            Boolean isOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if(isOrder){
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }

        Boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(isExistOrder){
            String orderId = (String)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)){
            return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
        }

        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

    /**
     * 更新库存
     * @param skuId
     */
    private void updateStockCount(Long skuId){
        Long stockCount = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        if(stockCount % 2 == 0){
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            seckillGoods.setStockCount(stockCount.intValue());//这里面存数量的意义是什么？
            seckillGoodsMapper.updateById(seckillGoods);

            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
        }
    }


}
