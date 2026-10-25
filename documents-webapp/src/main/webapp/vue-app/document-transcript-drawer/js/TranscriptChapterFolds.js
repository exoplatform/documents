/*
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
 */

import {countOccurrences} from './TranscriptSearch.js';

/**
 * The folds of a transcript's chapters, for the component that shows them
 * from its `groups`, `activeIndex`, `playingSeconds` and `term`: every
 * chapter is folded on open, and unfolds when the user opens it, picks it in
 * the list, plays into it or searches a term it holds.
 */
export default {
  data: () => ({
    opened: {},
  }),
  computed: {
    activeGroupKey() {
      return this.groupKeyOf(this.activeIndex);
    },
  },
  watch: {
    activeGroupKey() {
      // a chapter the user folds while it plays stays folded until the playback leaves it
      if (this.playingSeconds !== null) {
        this.openChapter(this.activeGroupKey);
      }
    },
    playingSeconds(seconds, previous) {
      if (previous === null && seconds !== null) {
        this.openChapter(this.activeGroupKey);
      }
    },
    chapters() {
      this.opened = {};
    },
  },
  methods: {
    /**
     * Finds the group a sentence is shown in.
     *
     * @param {number} index the sentence's index, or -1
     * @returns {string} the group's key, or null
     */
    groupKeyOf(index) {
      return index < 0 ? null : this.groups.find(group => group.rows.some(row => row.index === index))?.key || null;
    },
    /**
     * Unfolds a chapter.
     *
     * @param {string} key the chapter's group key, or null
     * @returns {void}
     */
    openChapter(key) {
      if (key && !this.opened[key]) {
        this.$set(this.opened, key, true);
      }
    },
    /**
     * Folds or unfolds a chapter.
     *
     * @param {string} key the chapter's group key
     * @returns {void}
     */
    toggleChapter(key) {
      this.$set(this.opened, key, !this.opened[key]);
    },
    /**
     * Unfolds every chapter holding an occurrence of the searched term; the
     * others keep the fold the user chose.
     *
     * @returns {void}
     */
    openMatchingChapters() {
      this.groups
        .filter(group => group.chapter && group.rows.some(row => countOccurrences(row.segment.text, this.term)))
        .forEach(group => this.openChapter(group.key));
    },
    /**
     * Goes to a chapter picked in the list: unfolded, shown, and played from
     * its start.
     *
     * @param {Object} group the chapter's group
     * @returns {void}
     */
    selectChapter(group) {
      this.openChapter(group.key);
      // the heading is in the DOM only once the list of chapters is rendered
      this.$nextTick(() => this.$refs[group.key]?.[0]?.$el?.scrollIntoView({block: 'start'}));
      this.$emit('seek', group.chapter.start);
    },
  },
};
