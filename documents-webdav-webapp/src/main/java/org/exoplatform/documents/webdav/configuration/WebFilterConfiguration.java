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

import java.util.EnumSet;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import io.meeds.spring.web.localization.HttpRequestLocaleFilter;
import io.meeds.spring.web.security.PortalIdentityFilter;
import io.meeds.spring.web.transaction.PortalTransactionFilter;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class WebFilterConfiguration {

  private static final String DRIVES_PATH_PATTERN = "/drives/*";

  @Bean
  public FilterRegistrationBean<PortalIdentityFilter> identityFilter() {
    FilterRegistrationBean<PortalIdentityFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new PortalIdentityFilter());
    registrationBean.setUrlPatterns(List.of(DRIVES_PATH_PATTERN));
    registrationBean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
    registrationBean.setOrder(2);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<HttpRequestLocaleFilter> httpRequestLocaleFilter() {
    FilterRegistrationBean<HttpRequestLocaleFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new HttpRequestLocaleFilter());
    registrationBean.setUrlPatterns(List.of(DRIVES_PATH_PATTERN));
    registrationBean.setOrder(4);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<PortalTransactionFilter> transactionFilter() {
    FilterRegistrationBean<PortalTransactionFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new PortalTransactionFilter());
    registrationBean.setUrlPatterns(List.of(DRIVES_PATH_PATTERN));
    registrationBean.setOrder(1);
    return registrationBean;
  }

}
