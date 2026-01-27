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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.exoplatform.documents.storage.jcr.listener;

import jakarta.annotation.PostConstruct;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;


@Component
public class MoveNodeListener extends Listener<Map<String, String>, String> {

    private final TrashStorage trashStorage;
    private final ListenerService listenerService;

    public MoveNodeListener(TrashStorage trashStorage, ListenerService listenerService) {
        this.trashStorage = trashStorage;
        this.listenerService = listenerService;
    }

    @PostConstruct
    public void init() {
        listenerService.addListener("exo-document-moved", this);
    }

    @Override
    public void onEvent(Event<Map<String, String>, String> event) throws Exception {
        Map<String, String> source = event.getSource();
        String newPath = source.get("destPath");
        String oldPath = source.get("srcPath");
        boolean isFolder = event.getData().equals(NT_FOLDER);
        if (isFolder) {
            trashStorage.updateRestorePath(oldPath, newPath);
        }
    }
}
