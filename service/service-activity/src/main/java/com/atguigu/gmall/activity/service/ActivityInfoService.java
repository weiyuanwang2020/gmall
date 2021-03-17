package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.ActivityRuleVo;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.SkuInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface ActivityInfoService extends IService<ActivityInfo> {

    /**
     * 分页查询
     * @param pageParam
     * @return
     */
    IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam);

    /**
     * 保存活动规则
     * @param activityRuleVo
     */
    void saveActivityRule(ActivityRuleVo activityRuleVo);


    List<SkuInfo> findSkuInfoByKeyword(String keyword);

    Map<String, Object> findActivityRuleList(Long activityId);

    /**
     * 根据skuId 找到活动规则
     * @param skuId
     * @return
     */
    List<ActivityRule> findActivityRule(Long skuId);

    /**
     * 获取购物项对应的活动规则列表
     * @param cartInfoList
     * @return
     */
    List<CarInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap);

    /**
     * 获取购物项中活动id对应的最优促销活动规则
     * @param orderDetailList  购物项列表
     * @return
     */
    Map<Long, ActivityRule> findTradeActivityRuleMap(List<OrderDetail> orderDetailList);


}
