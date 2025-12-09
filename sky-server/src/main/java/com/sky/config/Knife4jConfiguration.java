package com.sky.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j配置类
 */
@Configuration
public class Knife4jConfiguration {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("苍穹外卖项目接口文档")
                        .description("苍穹外卖项目接口文档")
                        .version("2.0")
                        .contact(new Contact()
                                .name("mikubob")
                                .email("2386782347@qq.com")));
    }
    
    /**
     * 管理端接口分组
     * @return
     */
    @Bean
    public GroupedOpenApi docket1() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .packagesToScan("com.sky.controller.admin")
                .build();
    }
    
    /**
     * 用户端接口分组
     * @return
     */
    @Bean
    public GroupedOpenApi docket2() {
        return GroupedOpenApi.builder()
                .group("用户端接口")
                .packagesToScan("com.sky.controller.user")
                .build();
    }
}