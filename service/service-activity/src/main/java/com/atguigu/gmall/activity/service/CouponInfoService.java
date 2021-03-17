package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface CouponInfoService extends IService<CouponInfo> {


    /**
     * 分页查询
     * @param pageParam
     * @return
     */
    IPage<CouponInfo> selectPage(Page<CouponInfo> pageParam);

    void saveCouponRule(CouponRuleVo couponRuleVo);

    Map<String, Object> findCouponRuleList(Long couponId);

    /**
     * 根据关键字获取优惠券列表，活动使用
     * @param keyword
     * @return
     */
    List<CouponInfo> findCouponByKeyword(String keyword);

    /**
     * 获取优惠券信息
     * @param skuId
     * @param activityId
     * @param userId
     * @return
     */
    List<CouponInfo> findCouponInfo(Long skuId, Long activityId, Long userId);

    void getCouponInfo(Long couponId, long userId);

    IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, long userId);

    /**
     * 获取购物项对应的优惠券列表
     * @param cartInfoList
     * @param skuIdToActivityIdMap 这个skuId是否存在对应的活动
     * @param userId  标识当前用户是否领取优惠劵
     * @return
     */
    Map<Long, List<CouponInfo>> findCartCouponInfo(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap, Long userId);

    /**
     * 获取交易购物项优惠券
     * @param orderDetailList
     * @param activityIdToActivityRuleMap
     * @param userId
     * @return
     */
    List<CouponInfo> findTradeCouponInfo(List<OrderDetail> orderDetailList, Map<Long, ActivityRule> activityIdToActivityRuleMap, Long userId);

    /**
     * 更新优惠券使用状态
     * @param couponId
     * @param userId
     * @param orderId
     */
    void updateCouponInfoUseStatus(Long couponId, Long userId, Long orderId);
}
