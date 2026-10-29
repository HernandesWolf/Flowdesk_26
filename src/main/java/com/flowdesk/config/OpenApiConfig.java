package com.flowdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI flowDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowDesk API")
                        .version("1.0.0")
                        .description("Multi-tenant service desk: customers, work orders, workflow, "
                                + "audit trail, SLA alerts and productivity reports."))
                .components(new Components()
                        .addSecuritySchemes(SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
