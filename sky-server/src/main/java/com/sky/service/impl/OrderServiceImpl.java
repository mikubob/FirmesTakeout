package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        /*//获取当前登录用户的id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),//商户订单号
                new BigDecimal("0.01"),//支付金额，单位 元
                "苍穹外卖订单",//商品名称
                user.getOpenid()//微信用户的openid
        );
        if(jsonObject.getString("code")!=null&&jsonObject.getString("code").equals("ORDERPAID")){
            throw new OrderBusinessException(MessageConstant.ORDER_PAID);
        }
        //封装VO数据并返回
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        //返回结果
        return vo;*/
        log.info("跳过微信支付，支付成功");
        // 无论如何都返回支付成功，不检查订单是否存在
        return new OrderPaymentVO();
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
            return;
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

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(userId);
        
        // 处理空字符串状态参数
        if (ordersPageQueryDTO.getStatus() != null && 
            (ordersPageQueryDTO.getStatus() == 0 || ordersPageQueryDTO.getStatus().toString().isEmpty())) {
            ordersPageQueryDTO.setStatus(null);
        }
        
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<OrderVO> page = orderMapper.pageQuery(ordersPageQueryDTO);
        long total = page.getTotal();
        List<OrderVO> results = page.getResult();
        for (OrderVO result : results) {
            Long orderId = result.getId();
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
            result.setOrderDetailList(orderDetailList);
        }

        return new PageResult(total, results);
    }
}