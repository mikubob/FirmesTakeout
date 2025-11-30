package com.sky.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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
                        .description("苍穹外卖项目接口文档\n联系人: mikubob (2386782347@qq.com)")
                        .version("2.0")
                        .contact(new Contact()
                                .name("mikubob")
                                .email("2386782347@qq.com")));
    }
}