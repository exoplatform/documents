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
package org.exoplatform.documents.storage.jcr.webdav.job;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.cache.CachedJcrWebDavService;

@RunWith(MockitoJUnitRunner.class)
public class WebDavUnlockJobTest {

  @Mock
  private CachedJcrWebDavService webDavService;

  @InjectMocks
  private WebDavUnlockJob        job;

  @Test
  public void testRunCallsUnlockTimedOutNodes() {
    job.run();
    verify(webDavService, times(1)).unlockTimedOutNodes();
  }
}
