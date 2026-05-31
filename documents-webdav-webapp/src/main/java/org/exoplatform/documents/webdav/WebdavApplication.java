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
package org.exoplatform.documents.webdav;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.PropertySource;

import io.meeds.spring.AvailableIntegration;
import io.meeds.spring.kernel.PortalApplicationContextInitializer;

@SpringBootApplication(scanBasePackages = {
  WebdavApplication.MODULE_NAME,
  AvailableIntegration.KERNEL_MODULE,
}, exclude = {
  DataJpaRepositoriesAutoConfiguration.class,
  LiquibaseAutoConfiguration.class,
})
@PropertySource("classpath:application.properties")
@PropertySource("classpath:documents-webdav.properties")
public class WebdavApplication extends PortalApplicationContextInitializer {

  public static final String MODULE_NAME = "org.exoplatform.documents.webdav";

}
