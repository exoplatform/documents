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

import java.util.UUID;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.DigestAuthenticationFilter;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;

@Configuration("webDigestAuthenticationFilterConfiguration")
public class WebDigestAuthenticationFilterConfiguration {

  @Bean
  public FilterRegistrationBean<DigestAuthenticationFilter> identityFilter(DigestAuthenticationFilter digestAuthenticationFilter) {
    FilterRegistrationBean<DigestAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(digestAuthenticationFilter);
    registrationBean.addUrlPatterns("/*");
    registrationBean.setOrder(2);
    return registrationBean;
  }

  @Bean
  public static UserDetailsService userDetailsService() {
    return new UserDetailsService() {
      @Override
      public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserACL userAcl = ExoContainerContext.getService(UserACL.class);
        Identity userIdentity = userAcl.getUserIdentity(username);
        if (userIdentity == null) {
          throw new UsernameNotFoundException(String.format("User with login %s doesn't exist", username));
        }
        return User.withUsername(username)
                   .password("password")
                   .authorities(userIdentity.getRoles().toArray(String[]::new))
                   .build();
      }
    };
  }

  @Bean
  public DigestAuthenticationEntryPoint authenticationEntryPoint() {
    DigestAuthenticationEntryPoint result = new DigestAuthenticationEntryPoint();
    result.setRealmName("webdavRealm");
    result.setKey(UUID.randomUUID().toString());
    return result;
  }

  @Bean
  public DigestAuthenticationFilter digestAuthenticationFilter(UserDetailsService userDetailsService,
                                                               DigestAuthenticationEntryPoint authenticationEntryPoint) {
    DigestAuthenticationFilter result = new DigestAuthenticationFilter();
    result.setUserDetailsService(userDetailsService);
    result.setAuthenticationEntryPoint(authenticationEntryPoint);
    result.setCreateAuthenticatedToken(true);
    return result;
  }

}
