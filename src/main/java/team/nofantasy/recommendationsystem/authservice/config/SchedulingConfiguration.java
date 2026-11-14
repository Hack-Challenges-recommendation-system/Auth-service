package team.nofantasy.recommendationsystem.authservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs, such as the daily cleanup of expired refresh tokens.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
