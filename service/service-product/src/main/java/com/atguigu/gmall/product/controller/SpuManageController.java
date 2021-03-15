package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product")
public class SpuManageController {

    @Autowired
    ManageService manageService;

    @GetMapping("{page}/{size}")
    public Result getSpuInfoPage(@PathVariable Long page, @PathVariable Long size, SpuInfo spuInfo){
        Page<SpuInfo> spuInfoPage = new Page<>(page, size);
        IPage<SpuInfo> iPage = manageService.getSpuInfoPage(spuInfoPage, spuInfo);
        return Result.ok(iPage);
    }

    // 销售属性http://api.gmall.com/admin/product/baseSaleAttrList
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){
        // 查询所有的销售属性集合
        List<BaseSaleAttr> baseSaleAttrList = manageService.getBaseSaleAttrList();

        return Result.ok(baseSaleAttrList);
    }

    /**
     * 保存spu
     * @param spuInfo
     * @return
     */
    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

    /**
     * 保存sku
     * @param skuInfo
     * @return
     */
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo) {
        // 调用服务层
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    /**
     * 根据关键字获取spu列表，活动使用
     * @param keyword
     * @return
     */
    @GetMapping("findSpuInfoByKeyword/{keyword}")
    public Result findSpuInfoByKeyword(@PathVariable("keyword") String keyword) {
        List<SpuInfo> spuInfoList = manageService.findSpuInfoByKeyword(keyword);
        return Result.ok(spuInfoList);
    }


}
