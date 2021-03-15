package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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
}
