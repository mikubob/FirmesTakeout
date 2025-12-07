package com.sky.mapper;

import com.sky.entity.DishFlavor;

import java.util.List;

public interface DishFlavorMapper {
    /**
     * 新增菜品口味
     * @param flavors
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     * 根据菜品id删除对应的口味数据
     * @param dishId
     */
    void deleteByDishId(Long dishId);

    /**
     * 根据id查询对应的口味数据
     *
     * @param id
     * @return
     */
    List<DishFlavor> getByDishId(Long id);
}
