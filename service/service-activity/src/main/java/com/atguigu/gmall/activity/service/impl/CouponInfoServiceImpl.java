package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.mapper.CouponRangeMapper;
import com.atguigu.gmall.activity.mapper.CouponUseMapper;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.CouponRangeType;
import com.atguigu.gmall.model.enums.CouponStatus;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.BaseCategory3;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Autowired
    private CouponInfoMapper couponInfoMapper;

    @Autowired
    private CouponRangeMapper couponRangeMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CouponUseMapper couponUseMapper;

    @Override
    public IPage<CouponInfo> selectPage(Page<CouponInfo> pageParam) {
        QueryWrapper<CouponInfo> couponInfoQueryWrapper = new QueryWrapper<>();
        couponInfoQueryWrapper.orderByDesc("id");
        IPage<CouponInfo> couponInfoIPage = couponInfoMapper.selectPage(pageParam, couponInfoQueryWrapper);
        couponInfoIPage.getRecords().forEach(couponInfo -> {
            couponInfo.setCouponTypeString(CouponType.getNameByType(couponInfo.getCouponType()));
            if(couponInfo.getRangeType() != null){
                couponInfo.setRangeTypeString(CouponRangeType.getNameByType(couponInfo.getRangeType()));
            }
        });
        return couponInfoIPage;
    }

    @Override
    public void saveCouponRule(CouponRuleVo couponRuleVo) {
        couponRangeMapper.delete(new QueryWrapper<CouponRange>().eq("coupon_id", couponRuleVo.getCouponId()));

        CouponInfo couponInfo = getById(couponRuleVo.getCouponId());
        couponInfo.setRangeType(couponRuleVo.getRangeType().name());
        couponInfo.setConditionAmount(couponRuleVo.getConditionAmount());
        couponInfo.setConditionNum(couponRuleVo.getConditionNum());
        couponInfo.setBenefitAmount(couponRuleVo.getBenefitAmount());
        couponInfo.setBenefitDiscount(couponRuleVo.getBenefitDiscount());
        couponInfo.setRangeDesc(couponRuleVo.getRangeDesc());
        updateById(couponInfo);

        List<CouponRange> couponRangeList = couponRuleVo.getCouponRangeList();
        for (CouponRange couponRange : couponRangeList) {
            couponRange.setCouponId(couponRuleVo.getCouponId());
            couponRangeMapper.insert(couponRange);
        }
    }

    @Override
    public Map<String, Object> findCouponRuleList(Long couponId) {
        Map<String, Object> map = new HashMap<>();
        List<CouponRange> couponRangeList = couponRangeMapper.selectList(new QueryWrapper<CouponRange>().eq("coupon_id", couponId));
        List<Long> rangeIdList = couponRangeList.stream().map(CouponRange::getRangeId).collect(Collectors.toList());
        if(!CollectionUtils.isEmpty(rangeIdList)){
            if("SPU".equals(couponRangeList.get(0).getRangeType())){
                List<SpuInfo> spuInfoList = productFeignClient.findSpuInfoBySpuIdList(rangeIdList);
                map.put("spuInfoList", spuInfoList);
            }else if(CouponRangeType.CATAGORY.name().equals(couponRangeList.get(0).getRangeType())){
                List<BaseCategory3> category3List = productFeignClient.findBaseCategory3ByCategory3IdList(rangeIdList);
                map.put("category3List", category3List);
            }else{
                List<BaseTrademark> trademarkList = productFeignClient.findBaseTrademarkByTrademarkIdList(rangeIdList);
                map.put("trademarkList", trademarkList);
            }
        }

        return map;
    }

    @Override
    public List<CouponInfo> findCouponByKeyword(String keyword) {
        QueryWrapper<CouponInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("coupon_name", keyword);
        return couponInfoMapper.selectList(queryWrapper);
    }

    @Override
    public List<CouponInfo> findCouponInfo(Long skuId, Long activityId, Long userId) {
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if(skuInfo == null) return new ArrayList<>();
        Long category3Id = skuInfo.getCategory3Id();
        Long tmId = skuInfo.getTmId();
        Long spuId = skuInfo.getSpuId();

        List<CouponInfo> couponInfoList = couponInfoMapper.selectCouponInfoList(spuId, category3Id, tmId, userId);
        if (activityId != null) {
            List<CouponInfo> activityCouponInfoList = couponInfoMapper.selectActivityCouponInfoList(spuId, category3Id, tmId, userId, activityId);
            couponInfoList.addAll(activityCouponInfoList);
        }

        return couponInfoList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void getCouponInfo(Long couponId, long userId) {
        CouponInfo couponInfo = getById(couponId);
        if(couponInfo.getTakenCount() >= couponInfo.getLimitNum()){
            throw new GmallException(ResultCodeEnum.COUPON_LIMIT_GET);
        }

        QueryWrapper<CouponUse> couponUseQueryWrapper = new QueryWrapper<>();
        couponUseQueryWrapper.eq("coupon_id", couponId);
        couponUseQueryWrapper.eq("user_id", userId);
        Integer count = couponUseMapper.selectCount(couponUseQueryWrapper);
        if(count > 0){
            throw new GmallException(ResultCodeEnum.COUPON_GET);
        }

        couponInfo.setTakenCount(couponInfo.getTakenCount() + 1);
        updateById(couponInfo);

        CouponUse couponUse = new CouponUse();
        couponUse.setCouponId(couponId);
        couponUse.setCouponStatus(CouponStatus.NOT_USED.name());
        couponUse.setExpireTime(couponInfo.getExpireTime());
        couponUse.setGetTime(new Date());
        couponUse.setUserId(userId);
        couponUseMapper.insert(couponUse);
    }

    @Override
    public IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, long userId) {
        return couponInfoMapper.selectPageByUserId(pageParam, userId);
    }

    @Override
    public Map<Long, List<CouponInfo>> findCartCouponInfo(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap, Long userId) {
        Map<Long, SkuInfo> skuIdToSkuInfoMap = new HashMap<>();
        List<SkuInfo> skuInfoList = new ArrayList<>();
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();

        for (CartInfo cartInfo : cartInfoList) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(cartInfo.getSkuId());
            skuIdToSkuInfoMap.put(cartInfo.getSkuId(), skuInfo);
            skuInfoList.add(skuInfo);
            setRuleData(skuInfo, rangeToSkuIdMap);
        }

        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectCartCouponInfoList(skuInfoList, userId);
        for (CouponInfo couponInfo : allCouponInfoList) {
            String rangeType = couponInfo.getRangeType();
            Long rangeId = couponInfo.getRangeId();

            if(couponInfo.getActivityId() != null){
                List<Long> skuIdList = new ArrayList<>();
                couponInfo.setSkuIdList(skuIdList);

                Iterator<Map.Entry<Long, Long>> iterator = skuIdToActivityIdMap.entrySet().iterator();
                while(iterator.hasNext()){
                    Map.Entry<Long, Long> entry = iterator.next();
                    Long skuId = entry.getKey();
                    Long activityId = entry.getValue();
                    if(couponInfo.getActivityId().longValue() == activityId.longValue()){
                        SkuInfo skuInfo = skuIdToSkuInfoMap.get(skuId);
                        if(rangeType.equals(CouponRangeType.SPU.name())){
                            if(rangeId.longValue() == skuInfo.getSpuId()){
                                skuIdList.add(skuId);
                            }
                        }else if(rangeType.equals(CouponRangeType.CATAGORY.name())){
                            if(rangeId.longValue() == skuInfo.getCategory3Id().longValue()){
                                skuIdList.add(skuId);
                            }
                        }else{
                            if(rangeId.longValue() == skuInfo.getTmId().longValue()){
                                skuIdList.add(skuId);
                            }
                        }
                    }
                }
            }else{
                if(rangeType.equals(CouponRangeType.SPU.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                }else if(rangeType.equals(CouponRangeType.CATAGORY.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else{
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }

        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = new HashMap<>();
        for (CouponInfo couponInfo : allCouponInfoList) {
            List<Long> skuIdList = couponInfo.getSkuIdList();
            if(!CollectionUtils.isEmpty(skuIdList)){
                for (Long skuId : skuIdList) {
                    if(!skuIdToCouponInfoListMap.containsKey(skuId)){
                        skuIdToCouponInfoListMap.put(skuId, new ArrayList<>());
                    }
                    skuIdToCouponInfoListMap.get(skuId).add(couponInfo);
                }
            }
        }

        return skuIdToCouponInfoListMap;
    }

    @Override
    public List<CouponInfo> findTradeCouponInfo(List<OrderDetail> orderDetailList, Map<Long, ActivityRule> activityIdToActivityRuleMap, Long userId) {
        // 优惠券范围规则数据
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();
        // 初始化数据，后续使用
        Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        // 初始化数据，后续使用
        Map<Long, SkuInfo> skuIdToSkuInfoMap = new HashMap<>();
        // 所以购物项的sku列表
        List<SkuInfo> skuInfoList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(orderDetail.getSkuId());
            skuInfoList.add(skuInfo);
            skuIdToSkuInfoMap.put(orderDetail.getSkuId(), skuInfo);
            skuIdToOrderDetailMap.put(orderDetail.getSkuId(), orderDetail);
            setRuleData(skuInfo, rangeToSkuIdMap);
        }

        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectTradeCouponInfoList(skuInfoList, userId);
        for (CouponInfo couponInfo : allCouponInfoList) {
            String rangeType = couponInfo.getRangeType();
            Long rangeId = couponInfo.getRangeId();

            if(couponInfo.getActivityId() != null){
                Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
                while(iterator.hasNext()){
                    Map.Entry<Long, ActivityRule> entry = iterator.next();
                    Long activityId = entry.getKey();
                    ActivityRule activityRule = entry.getValue();

                    List<Long> skuIdList = activityRule.getSkuIdList();
                    List<Long> couponSkuIdList = new ArrayList<>();
                    couponInfo.setSkuIdList(couponSkuIdList);
                    for (Long skuId : skuIdList) {
                        SkuInfo skuInfo = skuIdToSkuInfoMap.get(skuId);
                        if(rangeType.equals(CouponRangeType.SPU.name())){
                            if(skuInfo.getSpuId().longValue() == rangeId.longValue()){
                                couponSkuIdList.add(skuId);
                            }
                        }else if(rangeType.equals(CouponRangeType.CATAGORY.name())){
                            if(skuInfo.getCategory3Id().longValue() == rangeId.longValue()){
                                couponSkuIdList.add(skuId);
                            }
                        }else{
                            if(skuInfo.getTmId().longValue() == rangeId.longValue()){
                                couponSkuIdList.add(skuId);
                            }
                        }
                    }
                }
            }else{
                if(rangeType.equals(CouponRangeType.SPU.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                }else if(rangeType.equals(CouponRangeType.CATAGORY.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else{
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }

        List<CouponInfo> resultCouponInfoList = new ArrayList<>();
        Map<Long, List<CouponInfo>> couponIdToListMap = allCouponInfoList.stream().collect(Collectors.groupingBy(CouponInfo::getId));
        Iterator<Map.Entry<Long, List<CouponInfo>>> iterator = couponIdToListMap.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<Long, List<CouponInfo>> entry = iterator.next();
            Long couponInfoId = entry.getKey();
            List<CouponInfo> couponInfoList = entry.getValue();

            List<Long> skuIdList = new ArrayList<>();
            for (CouponInfo couponInfo : couponInfoList) {
                skuIdList.addAll(couponInfo.getSkuIdList());
            }
            CouponInfo couponInfo = couponInfoList.get(0);
            couponInfo.setSkuIdList(skuIdList);
            resultCouponInfoList.add(couponInfo);
        }

        //记录最优选项金额
        BigDecimal checkeAmount = new BigDecimal("0");
        //记录最优优惠券
        CouponInfo checkeCouponInfo = null;
        for (CouponInfo couponInfo : resultCouponInfoList) {
            BigDecimal totalAmount = new BigDecimal("0");
            int totalNum = 0;
            BigDecimal reduceAmount = new BigDecimal("0");

            List<Long> skuIdList = couponInfo.getSkuIdList();
            for (Long skuId : skuIdList) {
                OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                totalAmount = totalAmount.add(orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum())));
                totalNum += orderDetail.getSkuNum();
            }

            if(couponInfo.getCouponType().equals(CouponType.CASH.name())) {
                reduceAmount = couponInfo.getBenefitAmount();
                //标记可选
                couponInfo.setIsSelect(1);
            } else if (couponInfo.getCouponType().equals(CouponType.DISCOUNT.name())) {
                BigDecimal skuDiscountTotalAmount = totalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10")));
                reduceAmount = totalAmount.subtract(skuDiscountTotalAmount);
                //标记可选
                couponInfo.setIsSelect(1);
            }  else if (couponInfo.getCouponType().equals(CouponType.FULL_REDUCTION.name())) {
                if (totalAmount.compareTo(couponInfo.getConditionAmount()) > -1) {
                    reduceAmount = couponInfo.getBenefitAmount();
                    //标记可选
                    couponInfo.setIsSelect(1);
                }
            } else {
                if(totalNum >= couponInfo.getConditionNum().intValue()) {
                    BigDecimal skuDiscountTotalAmount1 = totalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10")));
                    reduceAmount = totalAmount.subtract(skuDiscountTotalAmount1);
                    //标记可选
                    couponInfo.setIsSelect(1);
                }
            }

            if(reduceAmount.compareTo(checkeAmount) > 0){
                checkeAmount = reduceAmount;
                checkeCouponInfo = couponInfo;
            }
            couponInfo.setReduceAmount(reduceAmount);
        }

        if (checkeCouponInfo != null) {
            for (CouponInfo couponInfo : resultCouponInfoList) {
                if(couponInfo.getId().longValue() == checkeCouponInfo.getId().longValue()){
                    couponInfo.setIsChecked(1);
                }
            }
        }


        return resultCouponInfoList;
    }

    @Override
    public void updateCouponInfoUseStatus(Long couponId, Long userId, Long orderId) {
        CouponUse couponUse = new CouponUse();
        couponUse.setOrderId(orderId);
        couponUse.setCouponStatus(CouponStatus.USED.name());
        couponUse.setUsingTime(new Date());

        QueryWrapper<CouponUse> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("coupon_id", couponId);
        queryWrapper.eq("user_id", userId);
        couponUseMapper.update(couponUse, queryWrapper);

    }

    private void setRuleData(SkuInfo skuInfo, Map<String, List<Long>> rangeToSkuIdMap){
        String key1 = "range:1:" + skuInfo.getSpuId();
        if(!rangeToSkuIdMap.containsKey(key1)){
            rangeToSkuIdMap.put(key1, new ArrayList<>());
        }
        rangeToSkuIdMap.get(key1).add(skuInfo.getId());

        String key2 = "range:2:" + skuInfo.getCategory3Id();
        if(!rangeToSkuIdMap.containsKey(key2)){
            rangeToSkuIdMap.put(key2, new ArrayList<>());
        }
        rangeToSkuIdMap.get(key2).add(skuInfo.getId());

        String key3 = "range:3:" + skuInfo.getTmId();
        if(!rangeToSkuIdMap.containsKey(key3)){
            rangeToSkuIdMap.put(key3, new ArrayList<>());
        }
        rangeToSkuIdMap.get(key3).add(skuInfo.getId());
    }

}
