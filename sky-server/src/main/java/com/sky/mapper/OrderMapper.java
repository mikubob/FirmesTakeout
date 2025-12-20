package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 根据id查询订单
     * @param id
     */
    Orders getById(Long id);

    /**
     * 分页查询订单数据
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据状态统计订单数量
     * @param toBeConfirmed
     * @return
     */
    Integer countStatus(Integer toBeConfirmed);

    /**
     * 根据状态和下单时间查询订单
     *
     * @param pendingPayment
     * @param time
     * @return
     */
    List<Orders> getByStatusAndOrdertimeLT(Integer pendingPayment, LocalDateTime time);
}
