package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.ActivityInfoMapper;
import com.atguigu.gmall.activity.mapper.ActivityRuleMapper;
import com.atguigu.gmall.activity.mapper.ActivitySkuMapper;
import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityInfoServiceImpl extends ServiceImpl<ActivityInfoMapper, ActivityInfo> implements ActivityInfoService {

    @Autowired
    private ActivityInfoMapper activityInfoMapper;

    @Autowired
    private ActivityRuleMapper activityRuleMapper;

    @Autowired
    private ActivitySkuMapper activitySkuMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CouponInfoMapper couponInfoMapper;


    @Override
    public IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam) {
        QueryWrapper<ActivityInfo> activityInfoQueryWrapper = new QueryWrapper<>();
        activityInfoQueryWrapper.orderByDesc("id");
        IPage<ActivityInfo> page = activityInfoMapper.selectPage(pageParam, activityInfoQueryWrapper);

        page.getRecords().forEach(item -> {
            item.setActivityTypeString(ActivityType.getNameByType(item.getActivityType()));
        });

        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveActivityRule(ActivityRuleVo activityRuleVo) {
        activityRuleMapper.delete(new QueryWrapper<ActivityRule>().eq("activity_id",activityRuleVo.getActivityId()));
        activitySkuMapper.delete(new QueryWrapper<ActivitySku>().eq("activity_id",activityRuleVo.getActivityId()));

        List<ActivityRule> activityRuleList = activityRuleVo.getActivityRuleList();
        List<ActivitySku> activitySkuList = activityRuleVo.getActivitySkuList();
        //后续处理
        CouponInfo couponInfo = new CouponInfo();
        couponInfo.setActivityId(0L);
        couponInfoMapper.update(couponInfo,new QueryWrapper<CouponInfo>().eq("activity_id",activityRuleVo.getActivityId()));

        for(ActivityRule activityRule : activityRuleList) {
            activityRule.setActivityId(activityRuleVo.getActivityId());
            activityRuleMapper.insert(activityRule);
        }

        for(ActivitySku activitySku : activitySkuList) {
            activitySku.setActivityId(activityRuleVo.getActivityId());
            activitySkuMapper.insert(activitySku);
        }

        List<Long> couponIdList = activityRuleVo.getCouponIdList();
        if(!CollectionUtils.isEmpty(couponIdList)){
            for (Long couponId : couponIdList) {
                CouponInfo couponInfoUp = couponInfoMapper.selectById(couponId);
                couponInfoUp.setActivityId(activityRuleVo.getActivityId());
                couponInfoMapper.updateById(couponInfoUp);
            }
        }
    }

    @Override
    public List<SkuInfo> findSkuInfoByKeyword(String keyword) {
        List<SkuInfo> skuInfoList = productFeignClient.findSkuInfoByKeyword(keyword);
        List<Long> skuIdList = skuInfoList.stream().map(SkuInfo::getId).collect(Collectors.toList());
        List<Long> existSkuIdList = activityInfoMapper.selectExistSkuIdList(skuIdList);
        List<SkuInfo> skuInfos = existSkuIdList.stream().map(skuId -> productFeignClient.getSkuInfo(skuId)).collect(Collectors.toList());
        skuInfoList.removeAll(skuInfos);
        return skuInfoList;
    }

    @Override
    public Map<String, Object> findActivityRuleList(Long activityId) {
        List<ActivityRule> activityRuleList = activityRuleMapper.selectList(new QueryWrapper<ActivityRule>().eq("activity_id", activityId));
        Map<String, Object> map = new HashMap<>();
        map.put("activityRuleList", activityRuleList);

        List<ActivitySku> activitySkuList = activitySkuMapper.selectList(new QueryWrapper<ActivitySku>().eq("activity_id", activityId));
        List<Long> skuIdList = activitySkuList.stream().map(ActivitySku::getSkuId).collect(Collectors.toList());
        List<SkuInfo> skuInfoList = productFeignClient.findSkuInfoBySkuIdList(skuIdList);
        map.put("skuInfoList", skuInfoList);

        QueryWrapper couponInfoQueryWrapper = new QueryWrapper<CouponInfo>();
        couponInfoQueryWrapper.eq("activity_id",activityId);
        List<CouponInfo> couponInfoList = couponInfoMapper.selectList(couponInfoQueryWrapper);
        map.put("couponInfoList", couponInfoList);

        return map;
    }

    @Override
    public List<ActivityRule> findActivityRule(Long skuId) {
        return activityInfoMapper.selectActivityRuleList(skuId);
    }

    @Override
    public List<CarInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap) {
        List<CarInfoVo> carInfoVoList = new ArrayList<>();

        Map<Long, CartInfo> skuIdToCartInfoMap = new HashMap<>();//id 与 cartinfo 相对应，为了好做后序处理
        for (CartInfo cartInfo : cartInfoList) {
            skuIdToCartInfoMap.put(cartInfo.getSkuId(), cartInfo);
        }

        List<Long> skuIdList = cartInfoList.stream().map(CartInfo::getSkuId).collect(Collectors.toList());
        List<ActivityRule> activityRuleList = activityInfoMapper.selectCartActivityRuleList(skuIdList);//可以算是有重复项

        Map<Long, List<ActivityRule>> skuIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getSkuId()));
        Map<Long, List<ActivityRule>> activityIdToActivityRuleListAllMap = activityRuleList.stream().collect(Collectors.groupingBy(activityRule -> activityRule.getActivityId()));

        Iterator<Map.Entry<Long, List<ActivityRule>>> iterator = activityIdToActivityRuleListAllMap.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<Long, List<ActivityRule>> entry = iterator.next();
            Long activityId = entry.getKey();
            List<ActivityRule> currentActivityRuleList = entry.getValue();
            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(ActivityRule::getSkuId).collect(Collectors.toSet());

            CarInfoVo carInfoVo = new CarInfoVo();
            List<CartInfo> curentCartInfoList = new ArrayList<>();
            for (Long skuId : activitySkuIdSet) {
                skuIdToActivityIdMap.put(skuId, activityId);
                curentCartInfoList.add(skuIdToCartInfoMap.get(skuId));
            }
            carInfoVo.setCartInfoList(curentCartInfoList);

            List<ActivityRule> skuActivityRuleList = skuIdToActivityRuleListMap.get(activitySkuIdSet.iterator().next());
            carInfoVo.setActivityRuleList(skuActivityRuleList);

            carInfoVoList.add(carInfoVo);
        }

        return carInfoVoList;
    }


}
