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
  <div class="recycle-bin">
    <div id="recycleBinHeader" class="d-flex align-center">
      <v-btn
        id="recycleBinBack"
        :title="$t('recycleBin.back')"
        :aria-label="$t('recycleBin.back')"
        class="ms-n2 me-1"
        small
        icon
        @click="$emit('back')">
        <v-icon size="18" class="text-color">
          {{ $vuetify.rtl && 'fa-arrow-right' || 'fa-arrow-left' }}
        </v-icon>
      </v-btn>
      <div
        id="recycleBinTitle"
        :title="title"
        class="text-title text-truncate"
        role="heading"
        aria-level="2">
        {{ title }}
      </div>
    </div>
    <div id="recycleBinRetentionInfo" class="text-subtitle">
      <template v-if="retentionDays">{{ $t('recycleBin.retention.info', {0: retentionDays}) }}</template>
    </div>
    <documents-recycle-bin-toolbar
      :selection-length="selection.length"
      :can-empty="!!total || !!name"
      :busy="busy"
      @filter="name = $event"
      @empty="$refs.confirmEmpty.open()"
      @restore-selection="restore(selection)"
      @delete-selection="confirmDelete(selection)" />
    <documents-recycle-bin-list
      ref="list"
      :bin="bin"
      :name="name"
      :drive="drive"
      :busy="busy"
      @selection="selection = $event"
      @total="total = $event"
      @error="displayError"
      @view="view"
      @restore="restore([$event])"
      @delete="confirmDelete([$event])" />
    <documents-recycle-bin-confirm-dialog
      ref="confirmDelete"
      :title="$t('recycleBin.confirm.delete.title')"
      :message="$t('recycleBin.confirm.delete.message')"
      dialog-id="recycleBinConfirmDelete"
      @confirm="remove(itemsToDelete)" />
    <documents-recycle-bin-confirm-dialog
      ref="confirmEmpty"
      :title="$t('recycleBin.confirm.empty.title')"
      :message="$t('recycleBin.confirm.empty.message')"
      dialog-id="recycleBinConfirmEmpty"
      @confirm="empty" />
  </div>
</template>
<script>
import {addActionEndListener, removeActionEndListener} from '../js/RecycleBinNotifications.js';

export default {
  props: {
    scope: {
      type: String,
      default: 'USER',
    },
    spaceId: {
      type: [String, Number],
      default: null,
    },
    ownerIdentityId: {
      type: [String, Number],
      default: null,
    },
    // The drive an administrator opened the bin of: {name, avatarUrl, type}
    openedDrive: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    space: null,
    retentionDays: null,
    name: null,
    selection: [],
    total: 0,
    itemsToDelete: [],
    // The bulk action this bin waits the end of: {actionId, kind}
    pending: null,
  }),
  computed: {
    bin() {
      return {
        scope: this.scope,
        spaceId: this.spaceId,
        ownerIdentityId: this.ownerIdentityId,
      };
    },
    busy() {
      return !!this.pending;
    },
    title() {
      return this.openedDrive?.name
          && this.$t('recycleBin.title.drive', {0: this.openedDrive.name})
          || this.$t('recycleBin.title');
    },
    // The drive of the bin, as each line shows it before the folder an item was in
    drive() {
      const space = this.scope === 'SPACE' || this.openedDrive?.type === 'SPACE';
      let avatarUrl = this.openedDrive?.avatarUrl || this.space?.avatarUrl || null;
      if (this.scope === 'USER') {
        avatarUrl = `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/${eXo.env.portal.userName}/avatar`;
      }
      return {
        avatarUrl,
        space,
        rootLabel: this.$t(space && 'recycleBin.location.root.space' || 'recycleBin.location.root.user'),
      };
    },
  },
  created() {
    addActionEndListener(this.actionEnded);
    if (this.scope === 'SPACE' && this.spaceId) {
      this.$spaceService.getSpaceById(this.spaceId)
        .then(space => this.space = space)
        .catch(() => this.space = null);
    }
    this.$recycleBinService.getSettings()
      .then(settings => this.retentionDays = settings?.retentionDays || null)
      .catch(this.displayError);
  },
  beforeDestroy() {
    removeActionEndListener(this.actionEnded);
  },
  methods: {
    view(item) {
      this.$recycleBinService.viewItem(item);
    },
    restore(items) {
      if (items.length === 1) {
        return this.$recycleBinService.restoreItem(this.bin, items[0].id)
          .then(() => this.done('restore', 1))
          .catch(this.failed);
      }
      return this.start('restore', items.length, actionId => this.$recycleBinService.restoreItems(this.bin, actionId, items.map(item => item.id)));
    },
    confirmDelete(items) {
      this.itemsToDelete = items.slice();
      this.$refs.confirmDelete.open();
    },
    remove(items) {
      if (items.length === 1) {
        return this.$recycleBinService.deleteItem(this.bin, items[0].id)
          .then(() => this.done('delete', 1))
          .catch(this.failed);
      }
      return this.start('delete', items.length, actionId => this.$recycleBinService.deleteItems(this.bin, actionId, items.map(item => item.id)));
    },
    empty() {
      return this.start('empty', null, actionId => this.$recycleBinService.emptyBin(this.bin, actionId));
    },
    // requested: the number of items the action is asked to treat, unknown for an Empty
    start(kind, requested, request) {
      const actionId = this.$recycleBinService.newActionId();
      this.pending = {actionId, kind, requested};
      return request(actionId).catch(error => {
        this.pending = null;
        this.failed(error);
      });
    },
    // An action that succeeds may still leave items out, one that left the bin
    // meanwhile as one that could not be read at its turn: what it treated is
    // compared with what it was asked, and what an Empty leaves with the list.
    actionEnded(action) {
      if (this.pending?.actionId === action.actionId) {
        const {kind, requested} = this.pending;
        this.pending = null;
        if (!action.succeeded) {
          this.partial(kind);
        } else if (kind === 'empty') {
          this.changed(kind).then(() => {
            if (this.total) {
              this.alertPartial(kind);
            } else {
              this.alertDone(kind, action.numberOfItems);
            }
          });
        } else if (action.numberOfItems === requested) {
          this.done(kind, action.numberOfItems);
        } else {
          this.partial(kind);
        }
      }
    },
    done(kind, count) {
      this.alertDone(kind, count);
      return this.changed(kind);
    },
    partial(kind) {
      this.alertPartial(kind);
      return this.changed(kind);
    },
    alertDone(kind, count) {
      this.$recycleBinService.displayAlert(this.$t(`recycleBin.${kind}.success`, {0: count}));
    },
    alertPartial(kind) {
      this.$recycleBinService.displayAlert(this.$t(`recycleBin.${kind}.partial`), 'warning');
    },
    failed(error) {
      this.displayError(error);
      // What was refused may have left the bin meanwhile: the list is read again.
      this.$refs.list.refresh();
    },
    // Reads the list again; resolved once it holds what the bin now has.
    changed(kind) {
      this.$emit('changed');
      if (kind === 'restore') {
        // The documents application of the page, when there is one, lists what came back.
        this.$root.$emit('documents-refresh-files');
      }
      return this.$refs.list.refresh();
    },
    displayError(error) {
      this.$recycleBinService.displayError(error, key => this.$t(key));
    },
  },
};
</script>
