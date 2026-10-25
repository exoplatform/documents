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
  <audio
    v-if="audio"
    ref="player"
    :src="`${url}#t=${start || 0.001}`"
    :autoplay="autoplay"
    controls="controls"
    class="full-width"
    @timeupdate="$emit('time', $event.target.currentTime)"
    @play="pauseOthers">
  </audio>
  <video
    v-else
    ref="player"
    :src="`${url}#t=${start || 0.001}`"
    :autoplay="autoplay"
    :style="compact && {maxHeight: '30vh'}"
    controls="controls"
    class="black full-width documentTranscriptPlayer"
    @timeupdate="$emit('time', $event.target.currentTime)"
    @play="pauseOthers">
  </video>
</template>
<script>
// one set for the page: the transcript drawer and the details' full view
// each bundle this player, and a hidden view keeps its player mounted
const players = window.documentsRecordingPlayers || (window.documentsRecordingPlayers = new Set());

export default {
  props: {
    url: {
      type: String,
      default: null,
    },
    start: {
      type: Number,
      default: 0,
    },
    autoplay: {
      type: Boolean,
      default: false,
    },
    audio: {
      type: Boolean,
      default: false,
    },
    compact: {
      type: Boolean,
      default: false,
    },
  },
  beforeDestroy() {
    const player = this.$refs.player;
    if (player) {
      player.pause();
      players.delete(player);
    }
  },
  methods: {
    /**
     * Keeps one recording playing at a time on the page: this player, once it
     * starts, pauses every other one that has played, shown or hidden.
     * @param {Event} event the play event
     * @returns {void}
     */
    pauseOthers(event) {
      const started = event.target;
      players.add(started);
      players.forEach(player => {
        if (player !== started && !player.paused) {
          player.pause();
        }
      });
    },
    /**
     * Moves the recording to a moment and plays it from there.
     *
     * @param {number} seconds the moment, in seconds from the start
     * @returns {void}
     */
    seek(seconds) {
      const player = this.$refs.player;
      if (player) {
        player.currentTime = seconds;
        player.play()?.catch(() => {
          // a browser that refuses to play without a user gesture still shows the moment
        });
      }
    },
  },
};
</script>
