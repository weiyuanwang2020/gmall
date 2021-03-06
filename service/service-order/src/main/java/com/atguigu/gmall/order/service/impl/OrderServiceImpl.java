package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.*;
import com.atguigu.gmall.order.mapper.*;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private OrderDetailActivityMapper orderDetailActivityMapper;

    @Autowired
    private OrderDetailCouponMapper orderDetailCouponMapper;

    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;

    @Autowired
    private ActivityFeignClient activityFeignClient;


    @Value("${ware.url}")
    private String WARE_URL;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrderInfo(OrderInfo orderInfo) {
        orderInfo.sumTotalAmount();
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setCreateTime(new Date());

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());

        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer tradeBody = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            tradeBody.append(orderDetail.getSkuName() + " ");
        }
        if(tradeBody.toString().length() > 100){
            orderInfo.setTradeBody(tradeBody.toString().substring(0, 100));
        }else{
            orderInfo.setTradeBody(tradeBody.toString());
        }

        orderInfo.setFeightFee(new BigDecimal("0"));
        orderInfo.setOperateTime(orderInfo.getCreateTime());
        BigDecimal activityReduceAmount = orderInfo.getActivityReduceAmount(orderInfo);
        orderInfo.setActivityReduceAmount(activityReduceAmount);

        orderInfoMapper.insert(orderInfo);

        Map<String, BigDecimal> skuIdToReduceAmountMap = orderInfo.computeOrderDetailPayAmount(orderInfo);
        // sku?????????????????????
        Map<Long, Long> skuIdToOrderDetailIdMap = new HashMap<>();

        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetail.setCreateTime(new Date());

            // ????????????????????????
            BigDecimal splitActivityAmount = skuIdToReduceAmountMap.get("activity:"+orderDetail.getSkuId());
            if(null == splitActivityAmount) {
                splitActivityAmount = new BigDecimal(0);
            }
            orderDetail.setSplitActivityAmount(splitActivityAmount);

            //?????????????????????
            BigDecimal splitCouponAmount = skuIdToReduceAmountMap.get("coupon:"+orderDetail.getSkuId());
            if(null == splitCouponAmount) {
                splitCouponAmount = new BigDecimal(0);
            }
            orderDetail.setSplitCouponAmount(splitCouponAmount);

            //?????????????????????
            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
            BigDecimal payAmount = skuTotalAmount.subtract(splitActivityAmount).subtract(splitCouponAmount);
            orderDetail.setSplitTotalAmount(payAmount);

            orderDetailMapper.insert(orderDetail);//???????????????

            skuIdToOrderDetailIdMap.put(orderDetail.getSkuId(), orderDetail.getId());
        }

        // ??????????????????????????????????????????????????????
        this.saveActivityAndCouponRecord(orderInfo, skuIdToOrderDetailIdMap);

        //??????????????????
        this.saveOrderStatusLog(orderInfo.getId(), orderInfo.getOrderStatus());


        //?????????????????????????????????????????????????????????
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL, MqConst.ROUTING_ORDER_CANCEL, orderInfo.getId(), MqConst.DELAY_TIME);


        return orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        // ??????key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // ?????????????????????
        String tradeNo = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);
        return tradeNo;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeCodeNo.equals(redisTradeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        String tradeNoKey = "user:" + userId + ":tradeCode";
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    @Override
    public void execExpiredOrder(Long orderId) {
        updateOrderStatus(orderId, ProcessStatus.CLOSED);

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
    }

    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    @Override
    public void sendOrderStatus(Long orderId) {
        this.updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);

        String wareJson = initWareOrder(orderId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);

    }
    // ??????orderId ??????json ?????????
    private String initWareOrder(Long orderId) {
        // ??????orderId ??????orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);

        // ???orderInfo????????????????????????Map
        Map map = initWareOrder(orderInfo);

        return JSON.toJSONString(map);
    }

    //  ???orderInfo????????????????????????Map
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// ??????Id ????????????????????????????????????
    /*
    details:[{skuId:101,skuNum:1,skuName:
    ????????????64G???},
    {skuId:201,skuNum:1,skuName:??????????????????}]
     */
        ArrayList<Map> mapArrayList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);
        return map;
    }

    @Override
    @Transactional
    public List<OrderInfo> orderSplit(Long orderId, String wareSkuMap) {
        List<OrderInfo> orderInfoList = new ArrayList<>();

        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        if(mapList != null){
            for (Map map : mapList) {
                String wareId = (String) map.get("wareId");
                List<String> skuIds = (List<String>) map.get("skuIds");

                OrderInfo subOrderInfo = new OrderInfo();
                BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(orderId);
                subOrderInfo.setWareId(wareId);

                List<OrderDetail> subList = new ArrayList<>();
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                if(orderDetailList != null && orderDetailList.size() > 0){
                    for (OrderDetail orderDetail : orderDetailList) {
                        for (String skuId : skuIds) {
                            if(Long.parseLong(skuId) == orderDetail.getSkuId().longValue()){
                                subList.add(orderDetail);
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(subList);
                subOrderInfo.sumTotalAmount();
                saveOrderInfo(subOrderInfo);

                orderInfoList.add(subOrderInfo);
            }
        }

        updateOrderStatus(orderId, ProcessStatus.SPLIT);
        return orderInfoList;
    }

    @Override
    public void execExpiredOrder(Long orderId,String flag) {
        // ???????????? ??????
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            // ??????????????????????????????????????????????????????
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }

    @Override
    public void saveOrderStatusLog(Long orderId, String orderStatus) {
        // ??????????????????
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(orderStatus);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }


    /**
     * ??????????????????????????????????????????????????????
     * @param orderInfo
     * @param skuIdToOrderDetailIdMap
     */
    private void saveActivityAndCouponRecord(OrderInfo orderInfo, Map<Long, Long> skuIdToOrderDetailIdMap) {
        //??????????????????
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                if(null != activityRule) {
                    for(Long skuId : activityRule.getSkuIdList()) {
                        OrderDetailActivity orderDetailActivity = new OrderDetailActivity();
                        orderDetailActivity.setOrderId(orderInfo.getId());
                        orderDetailActivity.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                        orderDetailActivity.setActivityId(activityRule.getActivityId());
                        orderDetailActivity.setActivityRule(activityRule.getId());
                        orderDetailActivity.setSkuId(skuId);
                        orderDetailActivity.setCreateTime(new Date());
                        orderDetailActivityMapper.insert(orderDetailActivity);
                    }
                }
            }
        }

        // ???????????????
        // ???????????????????????????
        Boolean isUpdateCouponStatus = false;
        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if(null != couponInfo) {
            List<Long> skuIdList = couponInfo.getSkuIdList();
            for (Long skuId : skuIdList) {
                OrderDetailCoupon orderDetailCoupon = new OrderDetailCoupon();
                orderDetailCoupon.setOrderId(orderInfo.getId());
                orderDetailCoupon.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                orderDetailCoupon.setCouponId(couponInfo.getId());
                orderDetailCoupon.setSkuId(skuId);
                orderDetailCoupon.setCreateTime(new Date());
                orderDetailCouponMapper.insert(orderDetailCoupon);

                // ???????????????????????????
                if(!isUpdateCouponStatus) {
                    activityFeignClient.updateCouponInfoUseStatus(couponInfo.getId(), orderInfo.getUserId(), orderInfo.getId());
                }
                isUpdateCouponStatus = true;
            }
        }
    }


}
