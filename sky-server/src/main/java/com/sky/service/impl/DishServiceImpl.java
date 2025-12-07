package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetMealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.utils.AliOssUtil;
import com.sky.vo.DishVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

@Service
public  class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetMealDishMapper setMealDishMapper;
    @Autowired
    private AliOssUtil aliOssUtil;
    
    /**
     * 新增菜品和对应的口味
     * @param dishDTO
     */
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        //拷贝属性
        dish.setName(dishDTO.getName());
        dish.setCategoryId(dishDTO.getCategoryId());
        dish.setPrice(dishDTO.getPrice());
        dish.setImage(dishDTO.getImage());
        dish.setDescription(dishDTO.getDescription());
        dish.setStatus(dishDTO.getStatus());

        //向菜品表中插入一条数据
        dishMapper.insert(dish);

        //获取insert语句生成的主键值
        Long dishId = dish.getId();

        //批量插入菜品口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors != null&&flavors.size()>0){
            flavors.forEach(dishFlavor -> dishFlavor.setDishId(dishId));
        }
        //向口味表中插入n条数据
        dishFlavorMapper.insertBatch(flavors);
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> page=dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 菜品批量删除
     * @param ids
     */
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能够删除---是否存在启售中的菜品？？
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){
                //当前菜品处于起售中的状态，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断当前菜品是否能够删除---是否被套餐关联了？？
        List<Long> setMealIds = setMealDishMapper.getSetMealIdsByDishIds(ids);
        if(setMealIds !=null && setMealIds.size()>0){
            //当前菜品被关联了不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //删除菜品中的相关数据
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            // 删除菜品图片
            if (dish.getImage() != null && !dish.getImage().isEmpty()) {
                try {
                    // 从URL中提取OSS对象名称
                    String objectName = getObjectKeyFromUrl(dish.getImage());
                    if (objectName != null) {
                        aliOssUtil.delete(objectName);
                    }
                } catch (Exception e) {
                    // 记录日志但不中断删除操作
                    e.printStackTrace();
                }
            }
            
            dishMapper.deleteById(id);
            //删除菜品关联的口味数据
            dishFlavorMapper.deleteByDishId(id);
        }
    }
    
    /**
     * 从完整的URL中提取OSS对象键
     * @param imageUrl 图片的完整URL
     * @return OSS对象键
     * @throws MalformedURLException
     */
    private String getObjectKeyFromUrl(String imageUrl) throws MalformedURLException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        // 解析URL并提取路径部分作为对象键
        URL url = new URL(imageUrl);
        String path = url.getPath();
        // 移除开头的斜杠
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
}