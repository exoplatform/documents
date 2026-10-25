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
  <div
    :aria-label="label"
    :class="blocks && 'documentTimelineBlocks' || 'documentTimelineLane'"
    class="documentTimelineTrack"
    role="group">
    <button
      v-for="(item, index) in items"
      :key="item.key"
      :ref="`item-${index}`"
      :title="item.title"
      :aria-label="item.label"
      :tabindex="index === focused && 0 || -1"
      :class="[item.background, item.text, item.key === activeKey && 'documentTimelineActive']"
      :style="[placement(item), item.style]"
      type="button"
      class="documentTimelineItem"
      @focus="focused = index"
      @keydown="move"
      @click="$emit('seek', item.start)">
      <span v-if="blocks" class="text-truncate">{{ item.caption }}</span>
    </button>
  </div>
</template>
<script>
/** The keys that move the focus along a track, by the step they take. */
const MOVES = {ArrowRight: 1, ArrowLeft: -1};

export default {
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    duration: {
      type: Number,
      default: 0,
    },
    label: {
      type: String,
      default: null,
    },
    blocks: {
      type: Boolean,
      default: false,
    },
    activeKey: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    focused: 0,
  }),
  watch: {
    /**
     * Brings the track's Tab stop back to its first item when its items change.
     * @returns {void}
     */
    items() {
      this.focused = 0;
    },
  },
  methods: {
    /**
     * Places an item on the track by its beginning and its length, as shares
     * of the recording.
     * @param {Object} item the item, with from and to in seconds
     * @returns {Object} its inline left and width
     */
    placement(item) {
      const duration = this.duration || 1;
      const from = Math.min(Math.max(item.from, 0), duration);
      const to = Math.min(Math.max(item.to, from), duration);
      return {
        insetInlineStart: `${from * 100 / duration}%`,
        width: `${(to - from) * 100 / duration}%`,
      };
    },
    /**
     * Moves the focus to the next or previous item of the track with the
     * arrow keys, or to its first or last with Home and End: the track is one
     * stop of the Tab key, however many items it has.
     * @param {KeyboardEvent} event the key pressed
     * @returns {void}
     */
    move(event) {
      const last = this.items.length - 1;
      let target = null;
      if (MOVES[event.key]) {
        // a right-to-left page runs the track from the right: the arrows swap
        const step = this.$vuetify.rtl ? -MOVES[event.key] : MOVES[event.key];
        target = Math.min(Math.max(this.focused + step, 0), last);
      } else if (event.key === 'Home') {
        target = 0;
      } else if (event.key === 'End') {
        target = last;
      }
      if (target !== null && last >= 0) {
        event.preventDefault();
        this.focused = target;
        this.$refs[`item-${target}`]?.[0]?.focus();
      }
    },
  },
};
</script>
