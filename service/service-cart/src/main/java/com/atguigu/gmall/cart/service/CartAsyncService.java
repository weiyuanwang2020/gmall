package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

public interface CartAsyncService {
    /**
     * 修改购物车
     * @param cartInfo
     */
    void updateCartInfo(CartInfo cartInfo);

    /**
     * 保存购物车
     * @param cartInfo
     */
    void saveCartInfo(CartInfo cartInfo);

    /**
     * 删除
     * @param userId
     */
    void deleteCartInfo(String userId);

    /**
     * 删除
     * @param userId
     * @param skuId
     */
    void deleteCartInfo(String userId, Long skuId);

    /**
     * 选中状态变更
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId, Integer isChecked, Long skuId);


}
