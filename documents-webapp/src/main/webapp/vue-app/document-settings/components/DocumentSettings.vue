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
  <v-app>
    <v-main>
      <v-card class="application-body py-2 px-6" flat>
        <extension-registry-components
          id="documentSettingsEntries"
          name="DocumentSettings"
          type="document-settings-entry"
          parent-element="div"
          element="div" />
      </v-card>
    </v-main>
  </v-app>
</template>
