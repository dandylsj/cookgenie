package com.cookgenie.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger(springdoc-openapi) 문서 설정. JWT Bearer 인증 스킴과 서버 목록을 등록한다. */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        Server ubuntuServer = new Server();
        ubuntuServer.setUrl("https://cookgenie.dandyhomelab.uk");
        ubuntuServer.setDescription("CookGenie Ubuntu Server");

        return new OpenAPI()
                .info(apiInfo())
                .addServersItem(localServer)
                .addServersItem(ubuntuServer)
                .addSecurityItem(securityRequirement)
                .components(components);
    }

    private Info apiInfo() {
        return new Info()
                .title("CookGenie API")
                .description("CookGenie API Document")
                .version("1.0.0");
    }
}
