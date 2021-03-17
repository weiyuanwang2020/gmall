package com.atguigu.gmall.activity.client;


import com.atguigu.gmall.activity.client.impl.ActivityDegradeFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderTradeVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "service-activity", fallback = ActivityDegradeFeignClient.class)
public interface ActivityFeignClient {

    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/api/activity/seckill/findAll")
    Result findAll();

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/api/activity/seckill/getSeckillGoods/{skuId}")
    Result getSeckillGoods(@PathVariable("skuId") Long skuId);

    /**
     * 秒杀确认订单
     * @return
     */
    @GetMapping("/api/activity/seckill/auth/trade")
    Result trade();

    /**
     * 获取购物车满足条件的促销与优惠券信息
     * @param cartInfoList
     * @return
     */
    @PostMapping("/api/activity/inner/findCartActivityAndCoupon/{userId}")
    List<CarInfoVo> findCartActivityAndCoupon(@RequestBody List<CartInfo> cartInfoList, @PathVariable("userId") Long userId);

    /**
     * 获取下单交易满足条件的促销与优惠券信息
     * @param orderDetailList
     * @return
     */
    @PostMapping("/api/activity/inner/findTradeActivityAndCoupon/{userId}")
    OrderTradeVo findTradeActivityAndCoupon(@RequestBody List<OrderDetail> orderDetailList, @PathVariable("userId") Long userId);

    /**
     * 更新优惠券使用状态
     * @param couponId
     * @param userId
     * @param orderId
     * @return
     */
    @GetMapping(value = "/api/activity/inner/updateCouponInfoUseStatus/{couponId}/{userId}/{orderId}")
    Boolean updateCouponInfoUseStatus(@PathVariable("couponId") Long couponId, @PathVariable("userId") Long userId, @PathVariable("orderId") Long orderId);


}
