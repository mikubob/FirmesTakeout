package com.sky.service;

import com.sky.dto.SetmealDTO;

public interface setmealservice {
    /**
     * 新增套餐
     * @param setmealDTO
     */
    void saveWithDish(SetmealDTO setmealDTO);
}
