package com.fertigate.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger OpenAPI 文档配置类
 * 配置API文档的基本信息、服务器地址、接口分组等
 * 访问地址：http://localhost:8080/swagger-ui.html
 * 原始JSON：http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 配置 OpenAPI 文档信息
     * 包括：
     * - API标题、描述、版本
     * - 联系方式
     * - 许可证信息
     * - 服务器地址
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("水肥一体化智能灌溉控制系统 API")
                        .version("1.0.0")
                        .description("""
                                水肥一体化智能灌溉控制系统后端API文档。
                                
                                ## 功能模块
                                
                                ### 1. 设备管理
                                - 传感器、电磁阀、施肥泵的注册、状态监控
                                - 在线/离线检测
                                
                                ### 2. 灌区管理
                                - 灌区划分、作物品种、种植面积
                                - 生育期记录
                                
                                ### 3. 阈值策略配置
                                - 土壤湿度上下限、EC/pH安全范围
                                - 气象联动条件
                                
                                ### 4. 分区轮灌调度
                                - 按灌区、时间、优先级生成轮灌计划
                                
                                ### 5. 手动/自动切换控制
                                - 支持手动启停阀门，自动模式按策略执行
                                
                                ### 6. 控制指令下发
                                - 通过 MQTT 下行控制电磁阀、施肥泵启停及开度
                                
                                ### 7. 安全联锁
                                - 通信中断、缺水（水压/流量低）、过载等异常时自动停止并告警
                                
                                ### 8. 灌肥台账记录
                                - 每次灌水/施肥的开始时间、结束时间、用量、执行方式（自动/手动）
                                
                                ## 访问方式
                                - Swagger UI: http://localhost:%s/swagger-ui.html
                                - API JSON: http://localhost:%s/v3/api-docs
                                """.formatted(serverPort, serverPort))
                        .contact(new Contact()
                                .name("Fertigate Control Team")
                                .email("support@fertigate.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地开发服务器"),
                        new Server()
                                .url("http://api.fertigate.com")
                                .description("生产环境服务器")
                ));
    }
}
