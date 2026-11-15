package team.nofantasy.recommendationsystem.authservice.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service",
                version = "0.0.1",
                description = "Authentication, verification, session and user management",
                license = @License(name = "MIT License")
        )
)
@Configuration
public class OpenApiConfiguration {
}
