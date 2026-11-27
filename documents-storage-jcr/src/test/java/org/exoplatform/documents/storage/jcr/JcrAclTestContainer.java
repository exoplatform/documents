/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.exoplatform.container.StandaloneContainer;

/**
 * Starts the kernel and the JCR alone, with the test access configuration
 * (`/jcr-acl/configuration.xml`), for the tests that run on a real repository
 */
final class JcrAclTestContainer {

  private JcrAclTestContainer() {
  }

  static StandaloneContainer start() throws Exception {
    StandaloneContainer.addConfigurationURL(JcrAclTestContainer.class.getResource("/jcr-acl/configuration.xml").toString());
    if (System.getProperty("java.security.auth.login.config") == null) {
      File login = File.createTempFile("login", ".conf");
      login.deleteOnExit();
      Files.writeString(login.toPath(), "exo-domain { org.exoplatform.services.security.jaas.DefaultLoginModule required; };");
      System.setProperty("java.security.auth.login.config", login.getAbsolutePath());
    }
    // Only the kernel's and the JCR's own configurations: the test classpath
    // carries other modules' ones, which need services these tests do not start
    ClassLoader parent = Thread.currentThread().getContextClassLoader();
    ClassLoader filtering = new ClassLoader(parent) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        if ("conf/configuration.xml".equals(name) || "conf/portal/configuration.xml".equals(name)) {
          List<URL> kept = new ArrayList<>();
          for (URL url : Collections.list(super.getResources(name))) {
            if (url.toString().contains("exo.kernel.component.common") || url.toString().contains("exo.jcr.component.core")) {
              kept.add(url);
            }
          }
          return Collections.enumeration(kept);
        }
        return super.getResources(name);
      }
    };
    Thread.currentThread().setContextClassLoader(filtering);
    try {
      return StandaloneContainer.getInstance();
    } finally {
      Thread.currentThread().setContextClassLoader(parent);
    }
  }
}
