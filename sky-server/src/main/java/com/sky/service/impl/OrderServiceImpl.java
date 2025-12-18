package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    //店铺地址
    @Value("${sky.shop.address}")
    private String shopAddress;
    //百度的ak
    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //1.异常情况的处理（收获地址为空、超出配送范围、购物车为空）
        AddressBook addressBook=addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook==null){
            //抛出业务异常
             throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //检查用户的收获地址是否在配送范围内
        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());
        ShoppingCart shoppingCart=new ShoppingCart();
        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList.isEmpty()||shoppingCartList==null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //2.向订单表插入一条数据
        Orders orders=new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        //下单时间
        orders.setOrderTime(LocalDateTime.now());
        //支付状态
        orders.setPayStatus(Orders.UN_PAID);
        //订单状态
        orders.setStatus(Orders.PENDING_PAYMENT);
        //通过使用时间戳和随机数对用户的订单号进行生成，防止订单号重复
        orders.setNumber(String.valueOf(System.currentTimeMillis())+(int)(Math.random()*1000000));
        //收获人电话
        orders.setPhone(addressBook.getPhone());
        //收获人
        orders.setConsignee(addressBook.getConsignee());
        //地址信息
        String address = addressBook.getProvinceName() + 
                        addressBook.getCityName() + 
                        addressBook.getDistrictName() + 
                        addressBook.getDetail();
        orders.setAddress(address);
        //用户id
        orders.setUserId(userId);

        orderMapper.insert(orders);

        //创建集合存储Detail数据
        List<OrderDetail> orderDetailList=new ArrayList<>();
        //3.向订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail=new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);
        //4.清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);
        //5.封装VO层数据并返回
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("模拟微信支付，支付成功");
        
        // 检查订单号是否为空
        if (ordersPaymentDTO.getOrderNumber() == null || ordersPaymentDTO.getOrderNumber().isEmpty()) {
            log.warn("订单号为空，尝试查找用户最新待支付订单");
            // 如果订单号为空，尝试查找当前用户最新的待支付订单
            Orders latestOrder = this.findLatestOrder(Orders.PENDING_PAYMENT, BaseContext.getCurrentId());
            ordersPaymentDTO.setOrderNumber(latestOrder.getNumber());
            log.info("找到用户最新待支付订单，订单号: {}", latestOrder.getNumber());
        }
        
        // 根据订单号查询订单信息
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        
        // 检查订单是否存在
        if (ordersDB == null) {
            log.warn("订单不存在，订单号：{}", ordersPaymentDTO.getOrderNumber());
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        
        // 直接更新订单状态为已支付
        paySuccess(ordersPaymentDTO.getOrderNumber());
        
        // 模拟微信支付返回参数，以便前端可以正常处理
        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setNonceStr("nonceStr"); //随机字符串
        vo.setPaySign("paySign"); //签名
        vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000)); //时间戳
        vo.setSignType("RSA"); //签名算法
        vo.setPackageStr("prepay_id=" + ordersPaymentDTO.getOrderNumber()); // 使用订单号作为prepay_id，保证唯一性
        
        return vo;
    }
    
    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        
        // 检查订单是否存在，如果不存在则直接返回，不抛出异常
        if (ordersDB == null) {
            log.warn("订单不存在，订单号：{}", outTradeNo);
            // 这里我们抛出一个异常，让调用方知道订单不存在
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }
    
    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);
        
        // 确保返回最新的订单状态
        log.info("查询订单详情，订单ID: {}, 状态: {}", id, orders.getStatus());

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 查询历史订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list =new ArrayList<>();

        for (Orders orders : page) {
            // 获取订单详情
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());
            
            // 构造OrderVO
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders,orderVO);
            orderVO.setOrderDetailList(orderDetails);
            
            // 如果有地址id，则从地址簿中获取详细地址信息
            if (orders.getAddressBookId() != null) {
                AddressBook addressBook = addressBookMapper.getById(orders.getAddressBookId());
                if (addressBook != null) {
                    // 拼接完整地址信息
                    String address = addressBook.getProvinceName() + 
                                   addressBook.getCityName() + 
                                   addressBook.getDistrictName() + 
                                   addressBook.getDetail();
                    orderVO.setAddress(address);
                }
            }
            
            list.add(orderVO);
        }

        return new PageResult(page.getTotal(),list);
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单请求参数: {}", ordersConfirmDTO);
        
        // 检查订单ID是否为空
        if (ordersConfirmDTO.getId() == null) {
            log.warn("订单ID为空，尝试查找最新的待接单订单");
            // 如果订单ID为空，尝试查找最新的待接单订单
            // 注意：管理端操作通常由管理员执行，不应限制用户ID
            Orders latestOrder = this.findLatestOrder(Orders.TO_BE_CONFIRMED);
            ordersConfirmDTO.setId(latestOrder.getId());
            log.info("找到最新待接单订单，订单ID: {}", latestOrder.getId());
        }
        
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
        
        // 添加日志以便调试
        log.info("订单已更新为已接单状态，订单ID: {}", ordersConfirmDTO.getId());
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        log.info("拒单请求参数: {}", ordersRejectionDTO);
        // 检查订单ID是否为空
        if (ordersRejectionDTO.getId() == null) {
            log.warn("订单ID为空，尝试查找最新的待接单订单");
            // 如果订单ID为空，尝试查找最新的待接单订单
            Orders latestOrder = this.findLatestOrder(Orders.TO_BE_CONFIRMED);
            ordersRejectionDTO.setId(latestOrder.getId());
            log.info("找到最新待接单订单，订单ID: {}", latestOrder.getId());
        }
        // 订单只有存在且状态为2（待接单）才可以拒单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
         /*
        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
        }
        */

        //拒单需要退款，根据订单id查询订单状态，拒单原因，取消时间
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
        //添加日志以便调试
        log.info("订单已更新为拒单状态，订单ID: {}", ordersRejectionDTO.getId());
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        log.info("取消订单请求参数: {}", ordersCancelDTO);
        
        // 检查订单ID是否为空
        if (ordersCancelDTO.getId() == null) {
            // 如果订单ID为空，尝试查找最新的待处理订单
            Orders latestOrder = this.findLatestOrder(null);
            ordersCancelDTO.setId(latestOrder.getId());
            log.info("找到最新待处理订单，订单ID: {}", latestOrder.getId());
        }
        
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        
        // 检查订单是否存在
        if (ordersDB == null) {
            log.warn("订单不存在，订单ID：{}", ordersCancelDTO.getId());
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        
        //管理端取消订单需要退款，根据订单id更新订单状态，取消原因，取消时间
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        
        // 如果订单已支付，标记为需要退款
        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            orders.setPayStatus(Orders.REFUND);
            log.info("订单已支付，标记为需要退款，订单ID: {}", ordersCancelDTO.getId());
            /*
            // 调用微信支付退款接口（在实际环境中启用）
            weChatPayUtil.refund(
                    ordersDB.getNumber(), // 商户订单号
                    ordersDB.getNumber(), // 商户退款单号
                    ordersDB.getAmount(), // 退款金额
                    ordersDB.getAmount()   // 原订单金额
            );
            */
        }
        
        orderMapper.update(orders);
        log.info("订单已取消，订单ID: {}", ordersCancelDTO.getId());
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        //根据id查询订单
        Orders ordersDB=orderMapper.getById(id);
        //判断订单是否存在,订单状态判断是否为3
        if(ordersDB==null||!ordersDB.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void complete(Long id) {
        //根据id查询订单
        Orders ordersDB=orderMapper.getById(id);
        //判断订单是否存在,订单状态判断是否为4
        if(ordersDB==null||!ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 用户取消订单
     * @param id
     * @throws Exception
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        Orders ordersDB=orderMapper.getById(id);
        //判断订单是否存在
        if(ordersDB==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if(ordersDB.getStatus()>2){
            //当订单状态不是待接单、待派送、接单时，不能取消订单
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //订单处于待接单、待派送、接单状态时，才可取消订单
        Orders orders=new Orders();
        orders.setId(ordersDB.getId());
        //订单处于待接单状态下取消，需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            //调用微信支付退款接口
            /*weChatPayUtil.refund(
                    ordersDB.getNumber(),//商户订单号
                    ordersDB.getNumber(),//商户退款单号
                    new BigDecimal(0.01),//退款金额
                    new BigDecimal(0.01)//原退款金额
            );*/
            // 支付状态改为退款
            orders.setPayStatus(Orders.REFUND);
        }
        //更新订单状态为取消，订单取消时间，取消原因
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //查询当前用户id
        Long userId = BaseContext.getCurrentId();
        //根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //将订单详情转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            //将订单详情里面的菜品信息重新赋给购物车对象
            BeanUtils.copyProperties(x, shoppingCart,"id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).toList();
        //将购物车数据批量插入到数据库中
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        // 封装成OrderStatisticsVO并返回
        OrderStatisticsVO orderStatisticsVO=new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 判断当前地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address){
        Map map=new HashMap<>();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);
        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.PARSE_ADDRESS_ERROR_SHOP);
        }
        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        //店铺的坐标
        String shopLngLat = location.getString("lng")+","+location.getString("lat");
        map.put("address",address);
        //获取用户地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        jsonObject=JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.PARSE_ADDRESS_ERROR_USER);
        }
        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        //用户的坐标
        String userLngLat = location.getString("lng")+","+location.getString("lat");

        //计算配送范围
        map.put("origins",shopLngLat);
        map.put("destinations",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String driving = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);
        jsonObject=JSON.parseObject(driving);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException(MessageConstant.ROUTE_PLANNING_FAILED);
        }
        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");
        //判断配送范围是否超出
        if(distance > 5000){
            throw new OrderBusinessException(MessageConstant.OUT_OF_DELIVERY_RANGE);
        }
    }

    /**
     * 获取订单菜品信息
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVOList(Page<Orders> page){
        //需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if(!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                String orderDishes = getOrderDishesStr(orders);

                //将订单菜品信息封装到OrderVO中，并且添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }
    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        //查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        //将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*2;）
        List<String> orderDishList = orderDetailList.stream().map(orderDetail -> orderDetail.getDishFlavor() + "*" + orderDetail.getNumber() + ";")
                .toList();
        //将每条订单菜品信息拼接为字符串
        return String.join("", orderDishList);
    }
    /**
     * 查找最新的订单
     *
     * @param status 订单状态 null表示不限制状态
     * @param userId 用户ID null表示不限制用户
     * @return 最新的订单
     */
    private Orders findLatestOrder(Integer status, Long userId) {
        OrdersPageQueryDTO queryDTO = new OrdersPageQueryDTO();
        queryDTO.setStatus(status);
        queryDTO.setUserId(userId);
        queryDTO.setPage(1);
        queryDTO.setPageSize(1);

        Page<Orders> page = orderMapper.pageQuery(queryDTO);
        if (page != null && !page.isEmpty()) {
            return page.get(0);
        }

        // 如果没找到订单，抛出异常
        throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
    }

    // 重载方法，只按状态查找
    private Orders findLatestOrder(Integer status) {
        return findLatestOrder(status, null);
    }

    // 重载方法，无条件查找
    private Orders findLatestOrder() {
        return findLatestOrder(null, null);
    }

    /**
     * 判断订单是否可以被取消
     * @param orders 订单对象
     * @return 是否可以取消
     */
    private boolean isOrderCancelable(Orders orders) {
        // 订单状态为待付款、待接单、已接单时可以取消
        return orders.getStatus() <= Orders.CONFIRMED && orders.getStatus() > 0;
    }
}