/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.document.cleanup.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flat WebSocket payload pushed to administrators while a campaign scan or
 * execution progresses. Never carries file paths, names or owners.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupWsMessage {

  public static final String PROGRESS_EVENT      = "campaign.progress";

  public static final String STATE_CHANGED_EVENT = "campaign.stateChanged";

  private String             wsEventName;

  private long               campaignId;

  private String             state;

  private long               processed;

  private long               total;

  private Long               etaSeconds;

}
