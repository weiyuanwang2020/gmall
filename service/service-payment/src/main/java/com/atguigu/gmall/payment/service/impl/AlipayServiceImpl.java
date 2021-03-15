package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.*;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AlipayClient alipayClient;

    @Override
    public String createaliPay(Long orderId) throws AlipayApiException {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        if ("CLOSED".equals(orderInfo.getOrderStatus())){
            return "该订单已经关闭了，不能生产二维码！";
        }

        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY.name());
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);

        Map<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        map.put("total_amount", "0.01");
        map.put("subject","test");
        alipayRequest.setBizContent(JSON.toJSONString(map));

        return alipayClient.pageExecute(alipayRequest).getBody();
    }

    @Override
    public boolean refund(Long orderId) {
        AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
        Map<String, Object> map = new HashMap<>();
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("refund_amount", "0.01");
        map.put("refund_reason", "他妈的，退钱！");
        refundRequest.setBizContent(JSON.toJSONString(map));
        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(refundRequest);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            // 更新交易记录 ： 关闭
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(),PaymentType.ALIPAY.name(), paymentInfo);
            return true;
        }
        return false;
    }

    @SneakyThrows
    @Override
    public Boolean closePay(Long orderId) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

        Map<String, Object> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("operator_id", "YX01");
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeCloseResponse response = alipayClient.execute(request);
        if(response.isSuccess()){
            return true;
        }else{
            return false;
        }
    }

    @SneakyThrows
    @Override
    public Boolean checkPayment(Long orderId) {
        // 根据订单Id 查询订单信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        // 根据out_trade_no 查询交易记录
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if(response.isSuccess()){
            return true;
        } else {
            return false;
        }

    }
}
