<!--

 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License
 as published by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <div class="recycle-bin-settings-entry">
    <v-list-item class="px-0">
      <v-list-item-content>
        <v-list-item-title class="text-title">
          {{ $t('recycleBin.title') }}
        </v-list-item-title>
      </v-list-item-content>
      <v-list-item-action>
        <v-btn
          id="documentSettingsRecycleBinEdit"
          :title="$t('recycleBin.configure.title')"
          :aria-label="$t('recycleBin.configure.title')"
          icon
          @click="$refs.configureDrawer.open()">
          <v-icon size="18" class="icon-default-color">fa-edit</v-icon>
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <documents-recycle-bin-configure-drawer ref="configureDrawer" />
  </div>
</template>
