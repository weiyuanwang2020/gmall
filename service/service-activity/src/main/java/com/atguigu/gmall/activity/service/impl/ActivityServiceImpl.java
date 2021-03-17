package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityInfoService activityInfoService;

    @Autowired
    private CouponInfoService couponInfoService;

    @Override
    public Map<String, Object> findActivityAndCoupon(Long skuId, long userId) {
        List<ActivityRule> activityRuleList = activityInfoService.findActivityRule(skuId);
        Long activityId = null;
        if(!CollectionUtils.isEmpty(activityRuleList)){
            activityId = activityRuleList.get(0).getActivityId();
        }
        List<CouponInfo> couponInfoList = couponInfoService.findCouponInfo(skuId, activityId, userId);

        Map<String, Object> map = new HashMap<>();
        map.put("activityRuleList", activityRuleList);
        map.put("couponInfoList", couponInfoList);

        return map;
    }

    @Override
    public List<CarInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        Map<Long, Long> skuIdToActivityIdMap = new HashMap<>();
        List<CarInfoVo> carInfoVoList = activityInfoService.findCartActivityRuleMap(cartInfoList, skuIdToActivityIdMap);
        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = couponInfoService.findCartCouponInfo(cartInfoList, skuIdToActivityIdMap, userId);

        List<CartInfo> noJoinCartInfoList = new ArrayList<>();
        for (CartInfo cartInfo : cartInfoList) {
            Boolean isJoinActivity = false;
            Iterator<Map.Entry<Long, Long>> iterator = skuIdToActivityIdMap.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<Long, Long> entry = iterator.next();
                Long skuId = entry.getKey();
                if(skuId.longValue() == cartInfo.getSkuId()){
                    isJoinActivity = true;
                    break;
                }
            }

            if(!isJoinActivity){
                noJoinCartInfoList.add(cartInfo);
            }
        }

        if(!CollectionUtils.isEmpty(noJoinCartInfoList)){
            CarInfoVo carInfoVo = new CarInfoVo();
            carInfoVo.setCartInfoList(noJoinCartInfoList);
            carInfoVo.setActivityRuleList(null);
            carInfoVoList.add(carInfoVo);
        }

        for (CarInfoVo carInfoVo : carInfoVoList) {
            List<CartInfo> cartInfoList1 = carInfoVo.getCartInfoList();
            for (CartInfo cartInfo : cartInfoList1) {
                cartInfo.setCouponInfoList(skuIdToCouponInfoListMap.get(cartInfo.getSkuId()));
            }
        }

        return carInfoVoList;
    }

    @Override
    public OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId) {
        OrderTradeVo orderTradeVo = new OrderTradeVo();

        Map<Long, ActivityRule> activityIdToActivityRuleMap = activityInfoService.findTradeActivityRuleMap(orderDetailList);

        Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        for (OrderDetail orderDetail : orderDetailList) {
            skuIdToOrderDetailMap.put(orderDetail.getSkuId(), orderDetail);
        }

        List<Long> activitySkuId = new ArrayList<>();
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        BigDecimal activityReduceAmount = new BigDecimal("0");
        if(!CollectionUtils.isEmpty(activityIdToActivityRuleMap)){
            Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
            while(iterator.hasNext()){
                OrderDetailVo orderDetailVo = new OrderDetailVo();
                orderDetailVoList.add(orderDetailVo);
                List<OrderDetail> detailList = new ArrayList<>();
                orderDetailVo.setOrderDetailList(detailList);

                Map.Entry<Long, ActivityRule> entry = iterator.next();
                Long activityId = entry.getKey();
                ActivityRule activityRule = entry.getValue();
                orderDetailVo.setActivityRule(activityRule);
                List<Long> skuIdList = activityRule.getSkuIdList();
                activitySkuId.addAll(skuIdList);
                for (Long skuId : skuIdList) {
                    OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                    detailList.add(orderDetail);
                }

                activityReduceAmount = activityReduceAmount.add(activityRule.getReduceAmount());
            }
        }

        List<OrderDetail> detailList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            if(!activitySkuId.contains(orderDetail.getSkuId())){
                detailList.add(orderDetail);
            }
        }
        OrderDetailVo orderDetailVo = new OrderDetailVo();
        orderDetailVo.setActivityRule(null);
        orderDetailVo.setOrderDetailList(detailList);
        orderDetailVoList.add(orderDetailVo);

        orderTradeVo.setActivityReduceAmount(activityReduceAmount);
        orderTradeVo.setOrderDetailVoList(orderDetailVoList);

        //优惠券处理，获取购物项能使用的优惠券
        List<CouponInfo> couponInfoList = couponInfoService.findTradeCouponInfo(orderDetailList, activityIdToActivityRuleMap, userId);
        orderTradeVo.setCouponInfoList(couponInfoList);

        return orderTradeVo;
    }
}
