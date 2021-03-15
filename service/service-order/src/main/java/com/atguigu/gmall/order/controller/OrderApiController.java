package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 确认订单
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        List<UserAddress> addressList = userFeignClient.findUserAddressListByUserId(userId);
        List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(userId);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (CartInfo cartInfo : cartInfoList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            orderDetailList.add(orderDetail);
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();

        String tradeNo = orderService.getTradeNo(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", addressList);
        result.put("detailArrayList", orderDetailList);
        result.put("totalNum", orderDetailList.size());
        result.put("totalAmount", orderInfo.getTotalAmount());
        result.put("tradeNo", tradeNo);

        return Result.ok(result);
    }

    /**
     * 提交订单
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        String tradeNo = request.getParameter("tradeNo");

        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if(!flag){
            return Result.fail().message("不能重复提交订单！");
        }

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        List<String> errorList = new ArrayList<>();
        List<CompletableFuture> futureList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!result) {
                    errorList.add(orderDetail.getSkuName() + "库存不足！");
                }
            }, threadPoolExecutor);
            futureList.add(checkStockCompletableFuture);

            CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (skuPrice.compareTo(orderDetail.getOrderPrice()) != 0) {
                    cartFeignClient.loadCartCache(userId);
                    errorList.add(orderDetail.getSkuName() + "价格有变动！");
                }
            }, threadPoolExecutor);
            futureList.add(checkPriceCompletableFuture);
        }

        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        if(errorList.size() > 0){
            return Result.fail().message(StringUtils.join(errorList, ","));
        }

        Long orderId = orderService.saveOrderInfo(orderInfo);
        orderService.deleteTradeNo(userId);
        return Result.ok(orderId);
    }

    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    /**
     * 拆单业务
     * @param request
     * @return
     */
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");

        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);
        List<Map> mapList = new ArrayList<>();
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            mapList.add(map);
        }
        return JSON.toJSONString(mapList);
    }

    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo) {
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }


}
