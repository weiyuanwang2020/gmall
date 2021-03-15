package com.atguigu.gmall.product.service.Impl;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaseTrademarkServiceImpl extends ServiceImpl<BaseTrademarkMapper, BaseTrademark> implements BaseTrademarkService {


    @Override
    public IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam) {

        return baseMapper.selectPage(pageParam, new QueryWrapper<BaseTrademark>().orderByDesc("id"));

    }

    @Override
    public List<BaseTrademark> findBaseTrademarkByKeyword(String keyword) {
        return baseMapper.selectList(new QueryWrapper<BaseTrademark>().like("tm_name", keyword));
    }

    @Override
    public List<BaseTrademark> findBaseTrademarkByTrademarkIdList(List<Long> trademarkIdList) {
        return baseMapper.selectBatchIds(trademarkIdList);
    }
}
