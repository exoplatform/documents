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

import java.util.function.Supplier;

import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.context.ServletContextAware;

import io.meeds.spring.web.security.PortalAuthenticationManager;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import lombok.Setter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration implements ServletContextAware {

  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfiguration.class);

  @Setter
  private ServletContext      servletContext;

  @Bean
  public static GrantedAuthorityDefaults grantedAuthorityDefaults() {
    // Reset prefix to be empty. By default it adds "ROLE_" prefix
    return new GrantedAuthorityDefaults("");
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http,
                                         PortalAuthenticationManager authenticationProvider,
                                         @Qualifier("restRequestMatcher")
                                         RequestMatcher restRequestMatcher,
                                         @Qualifier("staticResourcesRequestMatcher")
                                         RequestMatcher staticResourcesRequestMatcher,
                                         @Qualifier("accessDeniedHandler")
                                         AccessDeniedHandler accessDeniedHandler,
                                         @Qualifier("requestAuthorizationManager")
                                         AuthorizationManager<RequestAuthorizationContext> requestAuthorizationManager) {
    return http.authenticationProvider(authenticationProvider)
               .csrf(CsrfConfigurer::disable)
               .headers(HeadersConfigurer::disable)
               .jee(Customizer.withDefaults())
               .authorizeHttpRequests(customizer -> {
                 try {
                   customizer.requestMatchers(restRequestMatcher)
                             .access(requestAuthorizationManager);
                 } catch (Exception e) {
                   LOG.error("Error configuring REST endpoints security manager", e);
                 }
                 customizer.requestMatchers(staticResourcesRequestMatcher)
                           .permitAll();
                 customizer.dispatcherTypeMatchers(DispatcherType.INCLUDE,
                                                   DispatcherType.FORWARD)
                           .permitAll();
               })
               .exceptionHandling(exceptionCustomizer -> exceptionCustomizer.accessDeniedHandler(accessDeniedHandler))
               .build();
  }

  @Bean("restRequestMatcher")
  public RequestMatcher restRequestMatcher() {
    return request -> Strings.CS.startsWith(request.getRequestURI(), servletContext.getContextPath() + "/rest/");
  }

  @Bean("staticResourcesRequestMatcher")
  public RequestMatcher staticResourcesRequestMatcher() {
    return request -> !Strings.CS.startsWith(request.getRequestURI(), servletContext.getContextPath() + "/rest/");
  }

  @Bean("accessDeniedHandler")
  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      LOG.warn("Access denied for path {} and method {}",
               request.getRequestURI(),
               request.getMethod(),
               accessDeniedException);
      if (!response.isCommitted()) {
        // Put exception into request scope (perhaps of use to a view)
        request.setAttribute(WebAttributes.ACCESS_DENIED_403, accessDeniedException);
        // Set the 403 status code.
        response.setStatus(HttpStatus.FORBIDDEN.value());
      }
    };
  }

  @Bean("requestAuthorizationManager")
  public AuthorizationManager<RequestAuthorizationContext> requestAuthorizationManager() {
    return (Supplier<? extends Authentication> authentication, RequestAuthorizationContext object) -> {
      Authentication userAuthentication = authentication.get();
      // Permit anonymous and authentication users to access
      // the REST endpoints and rely on jee & secured permission
      // management
      return userAuthentication.isAuthenticated() ? new AuthorizationDecision(true) : new AuthorizationDecision(false);
    };
  }

  /**
   * @return {@link StrictHttpFirewall} which customizes the Allowed HTTP
   *         Methods
   */
  @Bean
  public StrictHttpFirewall httpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowedHttpMethods(ALLOW_METHODS_LIST);
    return firewall;
  }

  @Bean
  public PortalAuthenticationManager authenticationManager() {
    return new PortalAuthenticationManager();
  }

}
