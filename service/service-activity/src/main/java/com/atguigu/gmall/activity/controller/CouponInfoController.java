package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.enums.CouponType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/activity/couponInfo")
public class CouponInfoController {

   @Autowired
   private CouponInfoService couponInfoService;


   @ApiOperation(value = "获取分页列表")
   @GetMapping("{page}/{limit}")
   public Result index(
           @PathVariable Long page,
           @PathVariable Long limit){
      Page<CouponInfo> pageParam = new Page<>(page, limit);
      IPage<CouponInfo> couponInfoIPage = couponInfoService.selectPage(pageParam);
      return Result.ok(couponInfoIPage);
   }

   @ApiOperation(value = "获取优惠券")
   @GetMapping("get/{id}")
   public Result getCouponById(@PathVariable String id){
      CouponInfo couponInfo = couponInfoService.getById(id);
      couponInfo.setCouponTypeString(CouponType.getNameByType(couponInfo.getCouponType()));
      return Result.ok(couponInfo);
   }

   @ApiOperation(value = "新增优惠券")
   @PostMapping("save")
   public Result save(@RequestBody CouponInfo couponInfo) {
      couponInfoService.save(couponInfo);
      return Result.ok();
   }

   @ApiOperation(value = "修改优惠券")
   @PutMapping("update")
   public Result updateById(@RequestBody CouponInfo couponInfo) {
      couponInfoService.updateById(couponInfo);
      return Result.ok();
   }

   @ApiOperation(value = "删除优惠券")
   @DeleteMapping("remove/{id}")
   public Result remove(@PathVariable Long id) {
      couponInfoService.removeById(id);
      return Result.ok();
   }

   @ApiOperation(value="根据id列表删除优惠券")
   @DeleteMapping("batchRemove")
   public Result batchRemove(@RequestBody List<Long> idList){
      couponInfoService.removeByIds(idList);
      return Result.ok();
   }

   @ApiOperation(value = "新增优惠券规则")
   @PostMapping("saveCouponRule")
   public Result saveCouponRule(@RequestBody CouponRuleVo couponRuleVo){
      couponInfoService.saveCouponRule(couponRuleVo);
      return Result.ok();
   }

   @ApiOperation(value = "获取优惠券信息")
   @GetMapping("findCouponRuleList/{id}")
   public Result findActivityRuleList(@PathVariable Long id) {
      return Result.ok(couponInfoService.findCouponRuleList(id));
   }

   /**
    * 根据关键字获取优惠券列表，活动使用
    * @param keyword
    * @return
    */
   @GetMapping("findCouponByKeyword/{keyword}")
   public Result findCouponByKeyword(@PathVariable("keyword") String keyword) {
      return Result.ok(couponInfoService.findCouponByKeyword(keyword));
   }


}
