package com.sky.mapper;

import com.sky.entity.DishFlavor;

import java.util.List;

public interface DishFlavorMapper {
    //新增菜品口味
    void insertBatch(List<DishFlavor> flavors);
}
