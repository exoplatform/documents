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
  <div id="recycleBinOverview" class="recycle-bin-overview">
    <div id="recycleBinOverviewInfo" class="pb-4">{{ $t('recycleBin.overview.info') }}</div>
    <div class="d-flex align-center text-header pb-2">
      <div id="recycleBinOverviewColumnDrive" class="flex-grow-1 text-start">{{ $t('recycleBin.overview.column.drive') }}</div>
      <div id="recycleBinOverviewColumnSize" class="recycle-bin-drive-size text-end text-no-wrap">
        {{ $t('recycleBin.overview.column.size') }}
        <v-icon
          class="icon-default-color ms-1"
          size="14"
          aria-hidden="true">
          fa-arrow-down
        </v-icon>
      </div>
    </div>
    <v-divider />
    <v-list class="pa-0 pt-1">
      <v-hover
        v-for="drive in drives"
        :key="drive.ownerIdentityId"
        v-slot="{hover}">
        <v-list-item
          class="recycle-bin-drive px-0"
          style="min-height: 36px"
          dense
          @focusin.native="focusedDrive = drive.ownerIdentityId"
          @focusout.native="focusedDrive = null">
          <v-list-item-avatar
            :tile="drive.type === 'SPACE'"
            class="me-3 my-1"
            size="28">
            <img
              :src="drive.avatarUrl"
              :class="drive.type === 'SPACE' && 'rounded'"
              class="object-fit-cover"
              alt="">
          </v-list-item-avatar>
          <v-list-item-content class="py-1">
            <v-list-item-title :title="drive.name" class="recycle-bin-drive-name text-body-1">
              {{ drive.name }}
              <span v-if="drive.deleted" class="text-subtitle">{{ $t('recycleBin.overview.drive.deleted') }}</span>
            </v-list-item-title>
          </v-list-item-content>
          <v-btn
            v-if="administratorAccessEnabled"
            :title="$t('recycleBin.overview.drive.open', {0: drive.name})"
            :aria-label="$t('recycleBin.overview.drive.open', {0: drive.name})"
            :style="{opacity: hover || noHover || focusedDrive === drive.ownerIdentityId ? 1 : 0}"
            class="recycle-bin-drive-open flex-shrink-0 mx-1"
            icon
            small
            @click="$emit('open-drive', drive)">
            <v-icon size="18" class="icon-default-color">fa-eye</v-icon>
          </v-btn>
          <div class="recycle-bin-drive-size flex-shrink-0 text-end text-no-wrap" style="min-width: 70px">{{ sizeOf(drive) }}</div>
        </v-list-item>
      </v-hover>
    </v-list>
    <div v-if="!loading && !drives.length" class="text-subtitle py-4">
      {{ $t('recycleBin.overview.empty') }}
    </div>
    <div ref="more" class="d-flex justify-center py-2">
      <v-btn
        v-if="hasMore"
        id="recycleBinOverviewLoadMore"
        :loading="loading"
        class="btn"
        @click="loadMore">
        {{ $t('recycleBin.overview.loadMore') }}
      </v-btn>
      <v-progress-circular
        v-else-if="loading"
        color="primary"
        size="24"
        indeterminate />
    </div>
  </div>
</template>
<script>
export default {
  data: () => ({
    drives: [],
    administratorAccessEnabled: false,
    hasMore: false,
    loading: false,
    pageSize: 20,
    lastQuery: 0,
    observer: null,
    // The drive whose line holds the keyboard focus: its action is displayed as on a hover.
    focusedDrive: null,
    // A screen with no pointer to hover with displays the action of every drive.
    noHover: !!window.matchMedia?.('(hover: none)')?.matches,
  }),
  created() {
    this.refresh();
  },
  mounted() {
    // The next page is read as the end of the list comes into view.
    if (window.IntersectionObserver) {
      this.observer = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) {
          this.loadMore();
        }
      });
      this.observer.observe(this.$refs.more);
    }
  },
  beforeDestroy() {
    this.observer?.disconnect?.();
  },
  methods: {
    refresh() {
      this.drives = [];
      this.hasMore = false;
      return this.load(0);
    },
    loadMore() {
      if (this.hasMore && !this.loading) {
        this.load(this.drives.length);
      }
    },
    load(offset) {
      const queryId = ++this.lastQuery;
      this.loading = true;
      return this.$recycleBinService.getDrives(offset, this.pageSize)
        .then(data => {
          if (queryId === this.lastQuery) {
            const known = this.drives.map(drive => drive.ownerIdentityId);
            this.drives.push(...(data?.entities || []).filter(drive => !known.includes(drive.ownerIdentityId)));
            this.administratorAccessEnabled = !!data?.administratorAccessEnabled;
            this.hasMore = offset + (data?.entities?.length || 0) < (data?.size || 0) && !!data?.entities?.length;
          }
        })
        .catch(error => {
          if (queryId === this.lastQuery) {
            this.hasMore = false;
            this.$recycleBinService.displayError(error, key => this.$t(key));
          }
        })
        .finally(() => {
          if (queryId === this.lastQuery) {
            this.loading = false;
            this.$nextTick(this.observeAgain);
          }
        });
    },
    // An end of list still in view after a page is told again, so that the next page follows.
    observeAgain() {
      if (this.observer && this.$refs.more) {
        this.observer.unobserve(this.$refs.more);
        this.observer.observe(this.$refs.more);
      }
    },
    sizeOf(drive) {
      const size = this.$recycleBinService.formatSize(drive.size);
      return `${size.value} ${this.$t(`recycleBin.size.unit.${size.unit}`)}`;
    },
  },
};
</script>
