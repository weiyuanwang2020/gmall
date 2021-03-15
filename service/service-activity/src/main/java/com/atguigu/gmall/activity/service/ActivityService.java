package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;
import java.util.Map;

public interface ActivityService {
    Map<String, Object> findActivityAndCoupon(Long skuId, long userId);

    List<CarInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId);
}
