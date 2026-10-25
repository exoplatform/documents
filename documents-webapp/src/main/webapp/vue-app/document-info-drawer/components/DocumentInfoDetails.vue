<!--
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
-->
<template>
  <div class="documentInfoDetails">
    <div class="documentInfoGroupTitle">{{ $t('documents.drawer.details.group.about') }}</div>
    <documents-info-row :label="$t('documents.drawer.details.type')" :value="typeLabel" />
    <documents-info-row v-if="!file.folder" :label="$t('documents.drawer.details.size')">
      <span>{{ sizeLabel }}</span>
      <span
        v-if="sizeWithVersionsLabel"
        :title="sizeWithVersionsLabel"
        class="text-sub-title text-truncate ms-2">
        · {{ sizeWithVersionsLabel }}
      </span>
    </documents-info-row>
    <slot name="about"></slot>
    <documents-info-row
      v-if="!file.folder"
      :label="$t('documents.drawer.details.views')"
      :value="viewsLabel" />

    <div class="documentInfoGroupTitle">{{ $t('documents.drawer.details.group.history') }}</div>
    <documents-info-row :label="$t('documents.drawer.details.created')">
      <document-info-person :identity="file.creatorIdentity" />
      <span :title="exactDate(file.createdDate)" class="text-sub-title text-no-wrap ms-1">
        · {{ dayLabel(file.createdDate) }}
      </span>
    </documents-info-row>
    <documents-info-row v-if="modifiedShown" :label="$t('documents.drawer.details.modified')">
      <document-info-person :identity="file.modifierIdentity" />
      <span :title="exactDate(file.modifiedDate)" class="text-sub-title text-no-wrap ms-1">
        · {{ dayLabel(file.modifiedDate) }}
      </span>
    </documents-info-row>

    <div class="documentInfoGroupTitle">{{ $t('documents.drawer.details.group.location') }}</div>
    <documents-info-row v-if="file.ownerIdentity" :label="$t('documents.details.drive.label')">
      <img
        :src="file.ownerIdentity.avatar"
        alt=""
        class="rounded me-2 flex-shrink-0"
        width="20"
        height="20">
      <a
        :href="drivePath"
        :title="driveName"
        class="font-weight-bold text-truncate">
        {{ driveName }}
      </a>
    </documents-info-row>
    <documents-info-row :label="$t('documents.drawer.details.location')">
      <breadcrumb
        :folder-id="file.parentFolderId"
        :is-mobile="isMobile"
        class="text-truncate" />
    </documents-info-row>
  </div>
</template>
<script>
import {formatDay, formatExact, toDate} from '../js/InfoDates.js';

// an upload's own writes land within a second of each other
const CREATION_WRITE_MILLIS = 5000;

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    icon: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    typeLabel() {
      return this.$t(`documents.label.type.${this.icon?.type}`);
    },
    sizeLabel() {
      return this.formatSize(this.file.size);
    },
    /**
     * Names the size the file takes with its former versions, only when they
     * take room of their own.
     * @returns {string} the label, or null when the versions add nothing
     */
    sizeWithVersionsLabel() {
      if (!(this.file.sizeWithVersions > this.file.size)) {
        return null;
      }
      return this.$t('documents.drawer.details.sizeWithVersions.value', {0: this.formatSize(this.file.sizeWithVersions)});
    },
    viewsLabel() {
      const views = Number(this.file.views) || 0;
      return this.$t(views === 1 && 'documents.drawer.details.views.one' || 'documents.drawer.details.views.many', {0: views});
    },
    /**
     * Hides « Modified » on a file nobody changed since it was created. The
     * upload itself writes the modification date a moment after the creation
     * date, most often with no modifier recorded, so a modification within
     * that moment, by nobody or by the creator, is the creation itself.
     * @returns {boolean} true when the modification is worth a row
     */
    modifiedShown() {
      const created = this.timeOf(this.file.createdDate);
      const modified = this.timeOf(this.file.modifiedDate);
      if (!modified) {
        return false;
      }
      const modifier = this.file.modifierIdentity?.remoteId;
      const byCreator = !modifier || modifier === this.file.creatorIdentity?.remoteId;
      return !byCreator || !created || Math.abs(modified - created) >= CREATION_WRITE_MILLIS;
    },
    driveName() {
      return this.file.ownerIdentity?.providerId === 'space' && this.file.ownerIdentity.name || this.$t('documents.label.userHomeDocuments');
    },
    drivePath() {
      if (this.file.ownerIdentity?.providerId === 'space') {
        const spaceId = this.file.ownerIdentity.avatar?.split('social/spaces/')[1]?.split('/avatar')[0];
        return `${eXo.env.portal.context}/s/${spaceId}/documents`;
      }
      return this.$documentsUtils.getPersonalDriveUrl();
    },
  },
  methods: {
    /**
     * Formats a size in bytes with its unit label.
     * @param {number} size the size in bytes
     * @returns {string} the size, as « 2 MB »
     */
    formatSize(size) {
      const formatted = this.$documentsUtils.getSize(size);
      return `${formatted.value} ${this.$t(`document.size.label.unit.${formatted.unit}`)}`;
    },
    /**
     * Reads a date's time.
     * @param {number|string|Date} value the date
     * @returns {number} its time in milliseconds, or 0 when there is none
     */
    timeOf(value) {
      return toDate(value)?.getTime() || 0;
    },
    /**
     * Names the day of a date, as « Yesterday, 14:27 ».
     * @param {number|string|Date} value the date
     * @returns {string} the label
     */
    dayLabel(value) {
      return formatDay(value, this);
    },
    /**
     * Formats a date in full, for the hover of its short label.
     * @param {number|string|Date} value the date
     * @returns {string} the date and time, or an empty string
     */
    exactDate(value) {
      return formatExact(value, this);
    },
  },
};
</script>
