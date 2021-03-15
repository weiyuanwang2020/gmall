package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        String cartKey = getCartKey(userId);
        if(!redisTemplate.hasKey(cartKey)){
            this.loadCartCache(userId);
        }

        CartInfo cartInfoExist = (CartInfo) redisTemplate.boundHashOps(cartKey).get(skuId.toString());
        if(cartInfoExist != null){
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            cartInfoExist.setUpdateTime(new Timestamp(new Date().getTime()));
            cartInfoExist.setIsChecked(1);
            cartAsyncService.updateCartInfo(cartInfoExist);
        }else{
            CartInfo cartInfo = new CartInfo();//为什么不直接用之前的变量？
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            //  在初始化的时候，添加购物车的价格 = skuInfo.price
            cartInfo.setCartPrice(skuInfo.getPrice());
            //  数据库不存在的，购物车的价格 = skuInfo.price
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
            cartAsyncService.saveCartInfo(cartInfo);
            cartInfoExist = cartInfo;
        }

        redisTemplate.boundHashOps(cartKey).put(skuId.toString(), cartInfoExist);
        setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId){
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = this.getCartList(userTempId);
            return cartInfoList;
        }
        if (!StringUtils.isEmpty(userId)) {
            List<CartInfo> cartTempList = getCartList(userTempId);
            if(!CollectionUtils.isEmpty(cartTempList)){
                cartInfoList = mergeToCartList(cartTempList, userId);
                deleteCartList(userTempId);
            }
            if(StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartTempList)){
                cartInfoList = getCartList(userId);
            }
        }
        return cartInfoList;
    }

    @Override
    public void deleteCart(Long skuId, String userId) {
        String cartKey = getCartKey(userId);
        cartAsyncService.deleteCartInfo(userId, skuId);

        //获取缓存对象
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(skuId.toString())){
            hashOperations.delete(skuId.toString());
        }
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId){
        cartAsyncService.checkCart(userId, isChecked, skuId);

        String cartKey = getCartKey(userId);
        BoundHashOperations hashOperations = redisTemplate.boundHashOps(cartKey);
        if(hashOperations.hasKey(skuId.toString())){
            CartInfo cartInfo = (CartInfo) hashOperations.get(skuId.toString());
            cartInfo.setIsChecked(isChecked);
            hashOperations.put(skuId.toString(), cartInfo);
            setCartKeyExpire(cartKey);
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        String cartKey = getCartKey(userId);
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        List<CartInfo> checkedList = null;
        if (cartInfoList != null && cartInfoList.size() > 0) {
            checkedList = cartInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked().intValue() == 1)
                    .collect(Collectors.toList());
        }
        return checkedList;
    }


    // 设置过期时间
    private void setCartKeyExpire(String cartKey){
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    // 获取购物车的key
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;
    }

    /**
     * 根据用户获取购物车
     * @param userId
     * @return
     */
    private List<CartInfo> getCartList(String userId){
        List<CartInfo> cartInfoList = new ArrayList<>();
        if(StringUtils.isEmpty(userId)){
            return cartInfoList;
        }

        String cartKey = getCartKey(userId);
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if(!CollectionUtils.isEmpty(cartInfoList)){
            cartInfoList.sort((o1, o2) ->
                    DateUtil.truncatedCompareTo(o2.getUpdateTime(), o1.getUpdateTime(), Calendar.SECOND));
        }else{
            cartInfoList = loadCartCache(userId);//为什么不先查询后排序
        }

        return cartInfoList;
    }


    /**
     * 通过userId 查询购物车并放入缓存！
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> loadCartCache(String userId){
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id", userId);
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(cartInfoQueryWrapper);
        if(CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList;
        }

        HashMap<String, CartInfo> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            map.put(cartInfo.getSkuId().toString(), cartInfo);
        }
        String cartKey = getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey, map);
        setCartKeyExpire(cartKey);

        cartInfoList.sort((o1, o2) -> DateUtil.truncatedCompareTo(o2.getUpdateTime(), o1.getUpdateTime(), Calendar.SECOND));

        return cartInfoList;
    }

    /**
     * 合并
     * @param cartTempList
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartTempList, String userId) {

        List<CartInfo> cartList = getCartList(userId);
        Map<Long, CartInfo> cartInfoMap = cartList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        for (CartInfo cartInfo : cartTempList) {
            Long skuId = cartInfo.getSkuId();
            if(cartInfoMap.containsKey(skuId)){
                CartInfo cartInfoLogin = cartInfoMap.get(skuId);
                cartInfoLogin.setSkuNum(cartInfo.getSkuNum() + cartInfoLogin.getSkuNum());
                cartInfoLogin.setUpdateTime(new Timestamp(new Date().getTime()));
                if(cartInfo.getIsChecked().intValue() == 1){
                    cartInfoLogin.setIsChecked(1);
                }

                QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
                cartInfoQueryWrapper.eq("user_id", cartInfoLogin.getUserId());
                cartInfoQueryWrapper.eq("sku_id", cartInfoLogin.getSkuId());
                cartInfoMapper.update(cartInfoLogin, cartInfoQueryWrapper);
            }else{
                cartInfo.setUserId(userId);
                cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
                cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));

                cartInfoMapper.insert(cartInfo);
            }
        }

        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    /**
     * 删除购物车
     * @param userTempId
     */
    private void deleteCartList(String userTempId){
        cartAsyncService.deleteCartInfo(userTempId);

        String cartKey = getCartKey(userTempId);
        if(redisTemplate.hasKey(cartKey)){
            redisTemplate.delete(cartKey);
        }
    }

}
