# 苍穹外卖 - Sky Take Out

## 项目简介

苍穹外卖是一个基于 Spring Boot 和 Vue 的前后端分离外卖订餐系统，适用于餐厅在线接受订单和管理业务。该系统包含管理端和用户端两个主要功能模块，提供了完整的外卖业务流程解决方案。

管理端提供给餐厅管理人员使用，包括员工管理、菜品分类管理、菜品管理、套餐管理、订单管理、数据统计等功能。

用户端面向消费者，包括微信登录、商品浏览、购物车、下单、订单管理、个人中心等功能。

## 技术架构

### 后端技术栈

- **Spring Boot 3.1.0** - 主要框架
- **Spring MVC** - MVC框架，用于构建Web应用程序
- **Spring Boot Test** - 单元测试框架
- **Spring Data Redis** - Redis数据访问抽象
- **Spring Cache** - 缓存抽象层
- **Spring WebSocket** - WebSocket支持实现实时通信
- **Spring Transaction** - 事务管理
- **Spring Scheduling** - 定时任务支持
- **MyBatis 3.0.0** - 持久层框架，简化数据库操作
- **MyBatis PageHelper 1.3.0** - MyBatis分页插件
- **Druid 1.2.20** - 数据库连接池和监控组件
- **MySQL 8.0+** - 关系型数据库，存储业务数据
- **Redis** - 缓存数据库，提升系统性能
- **JWT (JJWT 0.11.5)** - 身份认证机制
- **Lombok 1.18.30** - 简化Java代码的工具库
- **FastJSON 1.2.76** - 高性能JSON处理库
- **Commons Lang 2.6** - Apache Commons语言工具类
- **AspectJ 1.9.4** - 面向切面编程框架，用于实现自动填充等功能
- **Knife4j 4.3.0** - API文档生成工具(适配Spring Boot 3.x)
- **阿里云 OSS SDK 3.10.2** - 阿里云对象存储服务SDK
- **Apache POI 3.16** - Java操作Microsoft Office格式文件的库
- **微信支付API v3** - 微信支付接口集成
- **JAXB API 2.3.1** - Java Architecture for XML Binding
- **Maven 3.6+** - 项目管理和构建工具

### 前端技术栈

- **Vue 3** - 渐进式JavaScript框架
- **Element Plus** - Vue 3组件库
- **Axios** - 网络请求库
- **Vite** - 构建工具

## 功能模块

### 管理端功能

1. **员工管理**
   - 员工登录/登出
   - 员工信息增删改查
   - 员工状态启用/禁用

2. **分类管理**
   - 菜品分类管理
   - 套餐分类管理
   - 分类状态启用/禁用

3. **菜品管理**
   - 菜品信息维护（含口味）
   - 菜品状态启用/禁用
   - 菜品批量删除

4. **套餐管理**
   - 套餐信息维护（包含菜品）
   - 套餐状态启用/禁用
   - 套餐批量删除

5. **订单管理**
   - 订单搜索和详情查看
   - 订单接单、派送、完成操作
   - 订单取消和拒单处理
   - 订单状态统计

6. **数据统计**
   - 营业额统计报表
   - 用户数量统计报表
   - 订单统计报表
   - 销量排名统计

7. **工作台**
   - 营业数据概览
   - 订单管理数据
   - 菜品总览
   - 套餐总览

### 用户端功能

1. **微信授权登录**
2. **商品浏览**
   - 菜品展示
   - 套餐展示
3. **购物车功能**
4. **订单功能**
   - 下单
   - 订单支付（微信支付）
   - 历史订单查询
   - 订单催单
5. **个人信息管理**
   - 收货地址管理
   - 个人资料编辑

## 项目结构

```
sky-take-out
├── nginx-1.20.2          # Nginx服务器
├── sky-common            # 公共模块（常量、工具类、异常等）
├── sky-pojo              # 实体类模块（DTO、VO、Entity）
├── sky-server            # 主服务模块（控制器、服务、映射器）
├── pom.xml               # Maven父工程配置
└── README.md             # 项目说明文档
```

### 模块详解

#### sky-common 公共模块
包含项目通用的工具类、常量定义、异常处理、配置类等公共组件。

#### sky-pojo 实体类模块
包含所有数据传输对象(DTO)、视图对象(VO)和实体类(Entity)，用于数据封装和传输。

#### sky-server 主服务模块
项目核心模块，包含控制器(Controller)、服务(Service)、数据访问(Mapper)等业务逻辑实现。

## 数据库设计

项目使用MySQL数据库，主要数据表包括：

- **employee** - 员工表
- **category** - 菜品/套餐分类表
- **dish** - 菜品表
- **dish_flavor** - 菜品口味表
- **setmeal** - 套餐表
- **setmeal_dish** - 套餐菜品关系表
- **user** - 用户表(微信用户)
- **address_book** - 地址簿表
- **shopping_cart** - 购物车表
- **orders** - 订单表
- **order_detail** - 订单明细表

## 运行环境

- JDK 21
- MySQL 8.0+
- Redis 5.0+
- Maven 3.6+
- Node.js 16+ (前端)

## 快速开始

1. 克隆项目到本地：
```bash
git clone <repository-url>
```

2. 导入数据库脚本：
```sql 
# 创建数据库并导入初始化脚本
CREATE DATABASE sky_take_out CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. 修改配置文件：
```bash
# 编辑开发环境配置文件
vim sky-server/src/main/resources/application-dev.yml
```

4. 启动Redis服务：
```bash
# 确保Redis服务正在运行，或使用Docker启动
docker run -d -p 6379:6379 --name redis redis:latest
```

5. 启动后端服务：
```bash
cd sky-server
mvn spring-boot:run
```

6. 启动前端项目：
```bash
# 前端项目通常在单独的仓库中
npm install
npm run dev
```

## API文档

项目集成了Knife4j API文档工具，可以通过 `/doc.html` 路径访问API文档。

分为两个分组：
1. 管理端接口
2. 用户端接口

访问地址：http://localhost:8080/doc.html

## 配置说明

主要配置项包括：

1. 数据库连接配置
2. Redis连接配置
3. JWT密钥和过期时间配置
4. 阿里云OSS配置
5. 微信支付配置

配置文件位于 `sky-server/src/main/resources/` 目录下：
- application.yml - 主配置文件
- application-dev.yml - 开发环境配置文件

## 部署说明

建议部署架构：
```
[用户] --> [Nginx反向代理] --> [前端静态资源]
                      |
                      --> [后端API服务]
                      
[Nginx] --> [MySQL数据库]
       |
       --> [Redis缓存]
```

## 开发规范

1. 使用RESTful API风格设计接口
2. 统一返回结果格式：`Result<T>`
3. 使用JWT进行身份认证
4. 使用MyBatis Generator自动生成DAO层代码
5. 使用Lombok简化实体类开发
6. 使用Swagger/Knife4j编写接口文档

## 注意事项

1. 项目使用JDK 21，请确保运行环境匹配
2. 部分功能依赖第三方服务（如阿里云OSS、微信支付），需要配置相应密钥
3. 开发过程中请遵循团队编码规范
4. 数据库表结构变更时请及时更新相关文档
5. 确保Redis服务正常运行，系统多个功能依赖Redis缓存
6. 报表统计功能需要确保数据库查询参数正确绑定

## 贡献者

- mikubob

## 版权信息

本项目仅供学习交流使用。