package io.github.frankie7techy.haraka.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI harakaOpenApi() {
        final String bearer = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("HarakaPay API")
                        .description("Mobile-money style wallet API: account registration, deposits, "
                                + "idempotent P2P transfers and a statement audit trail backed by a "
                                + "double-entry ledger.")
                        .version("v1.0.0")
                        .contact(new Contact().name("Frankie Ngaruiya").url("https://github.com/Frankie7techy")))
                .components(new Components().addSecuritySchemes(bearer,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(bearer));
    }
}
