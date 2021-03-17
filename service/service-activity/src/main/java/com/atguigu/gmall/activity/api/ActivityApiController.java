package com.atguigu.gmall.activity.api;

import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.cart.CarInfoVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderTradeVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/activity")
public class ActivityApiController {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private CouponInfoService couponInfoService;

    @ApiOperation(value = "根据skuId获取促销与优惠券信息")
    @GetMapping("findActivityAndCoupon/{skuId}")
    public Result findActivityAndCoupon(@PathVariable Long skuId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        if(StringUtils.isEmpty(userId)) userId = "0";
        Map<String, Object> map = activityService.findActivityAndCoupon(skuId, Long.parseLong(userId));
        return Result.ok(map);
    }

    @ApiOperation(value = "领取优惠券")
    @GetMapping(value = "auth/getCouponInfo/{couponId}")
    public Result getCouponInfo(@PathVariable("couponId") Long couponId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        couponInfoService.getCouponInfo(couponId, Long.parseLong(userId));
        return Result.ok();
    }

    @ApiOperation(value = "我的优惠券")
    @GetMapping("auth/{page}/{limit}")
    public Result index(
            @PathVariable Long page,
            @PathVariable Long limit,
            HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        Page<CouponInfo> pageParam = new Page<>(page, limit);
        IPage<CouponInfo> pageModel = couponInfoService.selectPageByUserId(pageParam, Long.parseLong(userId));
        return Result.ok(pageModel);
    }

    @ApiOperation(value = "获取购物车满足条件的促销与优惠券信息")
    @PostMapping("inner/findCartActivityAndCoupon/{userId}")
    public List<CarInfoVo> findCartActivityAndCoupon(@RequestBody List<CartInfo> cartInfoList, @PathVariable("userId") Long userId) {
        return activityService.findCartActivityAndCoupon(cartInfoList, userId);
    }

    @ApiOperation(value = "获取交易满足条件的促销与优惠券信息")
    @PostMapping("inner/findTradeActivityAndCoupon/{userId}")
    public OrderTradeVo findTradeActivityAndCoupon(@RequestBody List<OrderDetail> orderDetailList, @PathVariable("userId") Long userId, HttpServletRequest request) {
        return activityService.findTradeActivityAndCoupon(orderDetailList, userId);
    }

    @ApiOperation(value = "更新优惠券使用状态")
    @GetMapping("inner/updateCouponInfoUseStatus/{couponId}/{userId}/{orderId}")
    public Boolean updateCouponInfoUseStatus(@PathVariable("couponId") Long couponId, @PathVariable("userId") Long userId, @PathVariable("orderId") Long orderId) {
        couponInfoService.updateCouponInfoUseStatus(couponId, userId, orderId);
        return true;
    }


}
