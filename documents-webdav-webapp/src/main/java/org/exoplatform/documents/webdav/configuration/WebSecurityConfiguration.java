/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.webdav.configuration;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ALLOW_METHODS_LIST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import io.meeds.spring.web.security.GrantedAuthorityDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class WebSecurityConfiguration {

  @Bean
  public static GrantedAuthorityDefaults grantedAuthorityDefaults() {
    // Reset prefix to be empty. By default it adds "ROLE_" prefix
    return new GrantedAuthorityDefaults();
  }

  @Bean
  public SecurityFilterChain filterChain(DigestAuthenticationEntryPoint authenticationEntryPoint,
                                         HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
               .headers(HeadersConfigurer::disable)
               .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
               .build();
  }

  @Bean
  public StrictHttpFirewall httpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowedHttpMethods(ALLOW_METHODS_LIST);
    return firewall;
  }

}
