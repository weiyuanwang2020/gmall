package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product/baseTrademark")
public class BaseTrademarkController {

    @Autowired
    private BaseTrademarkService baseTrademarkService;

    @ApiOperation(value = "分页列表")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page, @PathVariable Long limit){
        Page<BaseTrademark> pageParam = new Page<>(page, limit);
        IPage<BaseTrademark> baseTrademarkIPage = baseTrademarkService.selectPage(pageParam);
        return Result.ok(baseTrademarkIPage);
    }

    /**
     * 查询全部品牌
     * @return
     */
    @GetMapping("getTrademarkList")
    public Result<List<BaseTrademark>> getTrademarkList() {
        List<BaseTrademark> trademarkList = baseTrademarkService.list(null);
        return Result.ok(trademarkList);
    }


    @ApiOperation("获取BaseTrademark")
    @GetMapping("get/{id}")
    public Result get(@PathVariable String id){
        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }

    @ApiOperation(value = "新增BaseTrademark")
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark banner){
        baseTrademarkService.save(banner);
        return Result.ok();
    }

    @ApiOperation(value = "修改BaseTrademark")
    @PutMapping("update")
    public Result updateById(@RequestBody BaseTrademark banner) {
        baseTrademarkService.updateById(banner);
        return Result.ok();
    }

    @ApiOperation(value = "删除BaseTrademark")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        baseTrademarkService.removeById(id);
        return Result.ok();
    }

    /**
     * 根据关键字获取spu列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("findBaseTrademarkByKeyword/{keyword}")
    public Result findBaseTrademarkByKeyword(@PathVariable("keyword") String keyword) {
        List<BaseTrademark> trademarkList=baseTrademarkService.findBaseTrademarkByKeyword(keyword);
        return Result.ok(trademarkList);
    }

    /**
     * 根据trademarkId列表获取trademark列表，活动使用
     * @param trademarkIdList
     * @return
     */
    @PostMapping("inner/findBaseTrademarkByTrademarkIdList")
    public List<BaseTrademark> findBaseTrademarkByTrademarkIdList(@RequestBody List<Long> trademarkIdList) {
        return baseTrademarkService.findBaseTrademarkByTrademarkIdList(trademarkIdList);
    }



}
