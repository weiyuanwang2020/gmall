package com.atguigu.gmall.activity.client.impl;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActivityDegradeFeignClient implements ActivityFeignClient {
    @Override
    public Result findAll() {
        return Result.fail();
    }

    @Override
    public Result getSeckillGoods(Long skuId) {
        return Result.fail();
    }

    @Override
    public Result trade() {

        return Result.fail();
    }

    @Override
    public List<CarInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        List<CarInfoVo> carInfoVoList = new ArrayList<>();
        CarInfoVo carInfoVo = new CarInfoVo();
        carInfoVo.setCartInfoList(cartInfoList);
        carInfoVo.setActivityRuleList(null);
        carInfoVoList.add(carInfoVo);
        return carInfoVoList;
    }
}
