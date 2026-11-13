package org.limelightcreatives.auth.login;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many login codes may be sent under {@code auth.limits:}.
 *
 * @param codesPerEmail to one address
 * @param codesPerIp from one IP address
 */
@ConfigurationProperties("auth.limits")
public record SignInLimits(int codesPerEmail, int codesPerIp) {
}
