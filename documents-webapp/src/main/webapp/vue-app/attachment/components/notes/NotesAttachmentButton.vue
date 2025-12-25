<!--
 * Copyright (C) 2025 eXo Platform SAS.
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
 *
-->

<template>
  <span v-if="displayAttachmentIcon">
    <v-btn
      :aria-label="$t('notes.open.attachments.list')"
      href="#notesAttachment"
      icon>
      <v-icon
        size="20"
        class="noteAttachmentsIcon">
        fa-solid fa-paperclip
      </v-icon>
    </v-btn>
  </span>
</template>
<script>

export default {
  data() {
    return {
      displayAttachmentIcon: false,
    };
  },
  created() {
    document.addEventListener('file-attachments-enable', this.enableAttachmentIcon);
    document.addEventListener('file-attachments-disable', this.disableAttachmentIcon);
  },
  beforeDestroy() {
    document.removeEventListener('file-attachments-enable', this.enableAttachmentIcon);
    document.removeEventListener('file-attachments-disable', this.disableAttachmentIcon);
  },
  methods: {
    enableAttachmentIcon() {
      this.displayAttachmentIcon = true;
    },
    disableAttachmentIcon() {
      this.displayAttachmentIcon = false;
    }
  },
};
</script>