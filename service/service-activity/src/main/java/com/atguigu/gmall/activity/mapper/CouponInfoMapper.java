package com.atguigu.gmall.activity.mapper;

import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponInfoMapper extends BaseMapper<CouponInfo> {

    List<CouponInfo> selectCouponInfoList(@Param("spuId") Long spuId,@Param("category3Id") Long category3Id,@Param("tmId") Long tmId,@Param("userId") Long userId);

    List<CouponInfo> selectActivityCouponInfoList(@Param("spuId") Long spuId,@Param("category3Id") Long category3Id,@Param("tmId") Long tmId,@Param("userId") Long userId,@Param("activityId") Long activityId);

    IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam,@Param("userId") long userId);

    /**
     * 获取购物车sku对应的优惠券列表
     * @param skuInfoList
     * @param userId
     * @return
     */
    List<CouponInfo> selectCartCouponInfoList(@Param("skuInfoList")List<SkuInfo> skuInfoList, @Param("userId")Long userId);

    List<CouponInfo> selectTradeCouponInfoList(List<SkuInfo> skuInfoList, Long userId);
}
