package com.fairshare.fairshare.common.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Fairshare API",
                version = "v1",
                description = "Expense sharing API with optional X-User-Id authentication and group membership authorization."
        )
)
@SecurityScheme(
        name = "user-id-header",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-User-Id",
        description = "Authenticated actor user id. Required when fairshare.auth.required=true."
)
public class OpenApiConfig {
}
