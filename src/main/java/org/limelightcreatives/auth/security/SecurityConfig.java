package org.limelightcreatives.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    static final String[] PUBLIC = {
            "/", "/login", "/login/code", "/error", "/actuator/health",
            "/css/**", "/js/**", "/fonts/**", "/favicon.ico"
    };

    @Bean
    @Order(2)
    SecurityFilterChain app(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests ->
                        requests.requestMatchers(PUBLIC)
                                .permitAll()
                                .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
        return http.build();
    }
}
