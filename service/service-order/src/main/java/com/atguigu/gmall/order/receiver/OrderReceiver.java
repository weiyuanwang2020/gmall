package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Component
public class OrderReceiver {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    /**
     * 取消订单消费者
     * 延迟队列，不能再这里做交换机与队列绑定
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        if(orderId != null){
            OrderInfo orderInfo = orderService.getById(orderId);
            if (null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())) {
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                if(paymentInfo != null && paymentInfo.getPaymentStatus().equals("UNPAID")){
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if(flag){
                        Boolean result = paymentFeignClient.closePay(orderId);
                        if(result){
                            orderService.execExpiredOrder(orderId, "2");
                        }
                    }else{
                        orderService.execExpiredOrder(orderId, "2");
                    }
                }else{
                    orderService.execExpiredOrder(orderId, "1");
                }
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}))
    public void udpOrder(Long orderId, Message message, Channel channel){
        if(orderId != null){
            OrderInfo orderInfo = orderService.getById(orderId);
            if(orderInfo != null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
                orderService.sendOrderStatus(orderId);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_WARE_ORDER)
    public void updateOrderStatus(String msgJson, Message message, Channel channel){
        if (!StringUtils.isEmpty(msgJson)) {
            Map<String, Object> map = JSON.parseObject(msgJson, Map.class);
            String status = (String) map.get("status");
            String orderId = (String) map.get("orderId");
            if ("DEDUCTED".equals(status)) {
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.WAITING_DELEVER);
            }else{
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.STOCK_EXCEPTION);
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}
