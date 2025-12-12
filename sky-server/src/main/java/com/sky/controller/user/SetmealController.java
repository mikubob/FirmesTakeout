package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.vo.DishItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sky.service.SetmealService;
import java.util.List;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Slf4j
@Tag(name = "用户端-套餐浏览相关接口")
public class SetmealController {
    @Autowired
    private SetmealService setmealService;

    /**
     * 根据分类id查询套餐
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询套餐")
    public Result<List<Setmeal>> list(Long categoryId){
        log.info("根据分类id查询套餐，{}",categoryId);
        Setmeal setmeal = Setmeal.builder()
                .status(StatusConstant.ENABLE)
                .id(categoryId)
                .build();
        return Result.success(setmealService.list(setmeal));
    }

    /**
     * 根据id获取套餐详情，包括套餐和菜品信息
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @Operation(summary = "根据id获取套餐详情，包括套餐和菜品信息")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id){
        log.info("根据id获取套餐详情，{}",id);
        List<DishItemVO> list=setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
