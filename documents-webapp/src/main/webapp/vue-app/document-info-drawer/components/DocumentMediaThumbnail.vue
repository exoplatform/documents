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
  <div class="d-flex flex-grow-1 position-relative overflow-hidden">
    <video
      v-if="kind === 'video'"
      :src="`${src}#t=0.1`"
      :aria-label="$t('documents.drawer.details.play')"
      class="documentMediaThumbnailVideo"
      preload="metadata"
      muted
      playsinline
      disablepictureinpicture
      @loadedmetadata="readDuration"
      @loadeddata="$emit('loaded')"
      @error="$emit('failed')"></video>
    <v-icon
      v-else
      size="80px"
      :class="icon.class"
      :color="icon.color"
      class="ma-auto" />
    <v-icon
      v-if="kind === 'video'"
      size="48"
      color="white"
      class="position-absolute documentMediaThumbnailPlay">
      fas fa-play-circle
    </v-icon>
    <span
      v-if="badge"
      class="position-absolute caption white--text px-1 rounded documentMediaThumbnailBadge">
      {{ badge }}
    </span>
  </div>
</template>
<script>
import {formatMoment} from '../../document-transcript-drawer/js/TranscriptSearch.js';

export default {
  props: {
    src: {
      type: String,
      default: null,
    },
    kind: {
      type: String,
      default: null,
    },
    icon: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    duration: null,
    audioProbe: null,
  }),
  computed: {
    badge() {
      return this.duration && formatMoment(this.duration) || null;
    },
  },
  watch: {
    src() {
      this.duration = null;
      this.probeAudio();
    },
  },
  mounted() {
    this.probeAudio();
  },
  beforeDestroy() {
    this.stopAudioProbe();
  },
  methods: {
    /**
     * Reads how long an audio file lasts from its metadata, through an audio
     * element that is never shown nor played: the thumbnail of an audio file
     * stays its icon.
     * @returns {void}
     */
    probeAudio() {
      this.stopAudioProbe();
      if (this.kind !== 'audio' || !this.src) {
        return;
      }
      const probe = new Audio();
      probe.preload = 'metadata';
      probe.addEventListener('loadedmetadata', this.readDuration);
      probe.src = this.src;
      this.audioProbe = probe;
    },
    /**
     * Drops the audio element reading the metadata, and its download.
     * @returns {void}
     */
    stopAudioProbe() {
      const probe = this.audioProbe;
      if (probe) {
        this.audioProbe = null;
        probe.removeEventListener('loadedmetadata', this.readDuration);
        probe.removeAttribute('src');
        probe.load();
      }
    },
    /**
     * Keeps how long the media lasts once the browser read its metadata, and
     * tells it to the details: a stream of unknown length reads as Infinity
     * and leaves it unknown.
     * @param {Event} event the loadedmetadata event of the media element
     * @returns {void}
     */
    readDuration(event) {
      const duration = event?.target?.duration;
      this.duration = Number.isFinite(duration) && duration > 0 ? duration : null;
      this.$emit('duration', this.duration);
    },
  },
};
</script>
