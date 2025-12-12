package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetMealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.utils.AliOssUtil;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetMealDishMapper setMealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 新增套餐，同时增加套餐和菜品关系
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        //向套餐表中插入数据
        setmealMapper.insert(setmeal);
        //获取生成的套餐id
        Long mealId=setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(mealId));
        //保存套餐和菜品的关系
        setMealDishMapper.insertBatch(setmealDishes);

    }

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page=setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        ids.forEach(id->{
            Setmeal setmeal = setmealMapper.getById(id);
            // 检查套餐是否存在
            if (setmeal == null) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
            if(StatusConstant.ENABLE==setmeal.getStatus()){
                //起售中的套餐不能被删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });
        ids.forEach(setmealId->{
            // 先删除套餐图片
            Setmeal setmeal = setmealMapper.getById(setmealId);
            if (setmeal != null && setmeal.getImage() != null && !setmeal.getImage().isEmpty()) {
                try {
                    String objectName = getObjectKeyFromUrl(setmeal.getImage());
                    if (objectName != null) {
                        aliOssUtil.delete(objectName);
                    }
                } catch (Exception e) {
                    // 记录日志但不中断删除操作
                    e.printStackTrace();
                }
            }
            
            //删除套餐表中的数据
            setmealMapper.deleteById(setmealId);
            //删除套餐关系表中的数据
            setMealDishMapper.deleteBySetmealId(setmealId);
        });
    }

    /**
     * 根据id查询套餐和套餐菜品的关系
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        Setmeal setmeal=setmealMapper.getById(id);
        List<SetmealDish> setmealDishes=setMealDishMapper.getBySetmealId(id);

        SetmealVO setmealVO=new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void update(SetmealDTO setmealDTO) {
        // 先获取原始套餐信息
        Setmeal originalSetmeal = setmealMapper.getById(setmealDTO.getId());

        // 检查是否需要删除原图片
        if (originalSetmeal != null && originalSetmeal.getImage() != null &&
            setmealDTO.getImage() != null && !originalSetmeal.getImage().equals(setmealDTO.getImage())) {
            // 图片发生了变化，需要删除原图片
            try {
                String objectName = getObjectKeyFromUrl(originalSetmeal.getImage());
                if (objectName != null) {
                    aliOssUtil.delete(objectName);
                }
            } catch (Exception e) {
                // 记录日志但不中断操作
                e.printStackTrace();
            }
        }
        
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        //1.修改套餐表，执行update
        setmealMapper.update(setmeal);

        //套餐id
        Long setmealId = setmealDTO.getId();

        //2.删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
        setMealDishMapper.deleteBySetmealId(setmealId);

        List<SetmealDish> setmealDishes=setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmealId));

        //3.重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
        setMealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐的起售停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未起售菜品，无法起售"
        if(status==StatusConstant.ENABLE){
            //select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = ?
            List<Dish> dishList=dishMapper.getBySetmealId(id);
            if(dishList != null&&dishList.size()>0){
                dishList.forEach(dish -> {
                    if(StatusConstant.DISABLE==dish.getStatus()){
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }


    /**
     * 从完整的URL中提取OSS对象键
     *
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