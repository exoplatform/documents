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
  <span>
    <component
      :is="piece.match && 'mark' || 'span'"
      v-for="(piece, index) in pieces"
      :key="index">{{ piece.value }}</component>
  </span>
</template>
<script>
import {splitOccurrences} from '../js/TranscriptSearch.js';

export default {
  props: {
    text: {
      type: String,
      default: '',
    },
    term: {
      type: String,
      default: '',
    },
  },
  computed: {
    pieces() {
      return splitOccurrences(this.text, this.term);
    },
  },
};
</script>
