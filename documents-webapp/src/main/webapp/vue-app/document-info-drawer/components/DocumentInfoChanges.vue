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
  <div id="documentInfoChanges" class="documentInfoChanges">
    <v-chip-group
      v-if="sources.length > 1"
      :value="selected"
      class="documentInfoChangesFilter pt-3"
      mandatory
      @change="select">
      <v-chip
        :value="ALL"
        class="documentInfoChangesFilter-all"
        active-class="primary--text"
        small
        outlined>
        {{ $t('documents.drawer.details.changes.filter.all') }}
      </v-chip>
      <v-chip
        v-for="source in sources"
        :key="source.id"
        :value="source.id"
        :class="`documentInfoChangesFilter-${source.id}`"
        active-class="primary--text"
        small
        outlined>
        {{ sourceLabel(source) }}
      </v-chip>
    </v-chip-group>
    <v-list dense class="pa-0">
      <component
        :is="sourceComponent(line.sourceId)"
        v-for="line in lines"
        :key="`${line.sourceId}-${line.entry.key}`"
        :entry="line.entry"
        :file="file"
        class="documentInfoChangesLine" />
    </v-list>
    <v-progress-linear v-if="loading" indeterminate />
    <div v-else-if="failed" class="text-sub-title py-4">
      {{ $t('documents.drawer.details.changes.error') }}
    </div>
    <div v-else-if="!lines.length && !hasMore" class="text-sub-title py-4">
      {{ $t('documents.drawer.details.changes.empty') }}
    </div>
    <div ref="end" class="documentInfoChangesEnd"></div>
  </div>
</template>
<script>
import {createTimeline} from '../js/ChangesTimeline.js';

/** The changes given out per scroll, and asked of each source per read. */
const PAGE_SIZE = 20;
/** The extension point under which another add-on feeds the changes. */
const EXTENSION_NAME = 'DocumentsInfoChanges';
/**
 * The type under which another add-on feeds the changes:
 * extensionRegistry.registerComponent('DocumentsInfoChanges', 'documents-info-changes-source', options),
 * the options being, the last registered per id kept:
 * - id: the source's id, unique, 'versions' being the file's own versions;
 * - labelKey, or label: the source's chip, shown once two sources apply;
 * - rank: its order among sources, lowest first, which also orders their
 *   changes of a same date;
 * - isEnabled({file, documentId}): optional, a boolean or a promise of one,
 *   whether the source applies to the file; a failure means it does not;
 * - load({file, documentId, before, limit}): a promise of {entries, next},
 *   entries being at most limit changes older than the cursor before (null
 *   for the first page), newest first, each as {time, key, ...}: time in
 *   milliseconds, key unique within the source and, among its changes of a
 *   same time, highest first; next being the cursor the following page is
 *   read from, given back as before, null once none is left. A page must
 *   neither repeat nor skip a change across reads, even when changes are
 *   added meanwhile: a cursor on (time, key) does that. A rejection drops
 *   the source and tells the reader some changes cannot be read;
 * - refreshEvent: optional, the name of a document event after which the
 *   source may hold new changes, such as a save made beside the list;
 * - isRefreshFor(detail, {file, documentId}): optional, whether such an
 *   event, by its detail, concerns the file shown; without it, every such
 *   event does;
 * - vueComponent: renders one change, given the props entry and file.
 * documentId is the file a shortcut points to, the file itself otherwise.
 */
const SOURCE_TYPE = 'documents-info-changes-source';
/** The id of the file's own versions, always a source. */
const VERSIONS_SOURCE = 'versions';

/**
 * @param {Object} version a version, as the versions REST writes it
 * @returns {number} its creation time in milliseconds, its date being
 *          written as {time}, or as a date string or a number
 */
function versionTime(version) {
  const date = version?.createdDate;
  return Number(date && typeof date === 'object' ? date.time : new Date(date).getTime()) || 0;
}

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    ALL: 'all',
    sources: [],
    selected: 'all',
    lines: [],
    loading: false,
    hasMore: false,
    failed: false,
    timeline: null,
    generation: 0,
    observer: null,
  }),
  computed: {
    /**
     * A shortcut shows the changes of the file it points to.
     * @returns {string} the id of the file whose changes show
     */
    documentId() {
      return this.file?.sourceID || this.file?.id || null;
    },
  },
  watch: {
    documentId() {
      this.loadSources();
    },
  },
  /**
   * Reads the sources, and again when an add-on registers one late.
   * @returns {void}
   */
  created() {
    document.addEventListener(`component-${EXTENSION_NAME}-${SOURCE_TYPE}-updated`, this.refreshSources);
    this.loadSources();
  },
  /**
   * Loads the following changes whenever the end of the list comes into view.
   * @returns {void}
   */
  mounted() {
    this.observer = new IntersectionObserver(observed => {
      if (observed.some(entry => entry.isIntersecting)) {
        this.loadMore();
      }
    });
    this.observer.observe(this.$refs.end);
  },
  /**
   * Drops every answer still to come and stops watching the list and the registry.
   * @returns {void}
   */
  beforeDestroy() {
    document.removeEventListener(`component-${EXTENSION_NAME}-${SOURCE_TYPE}-updated`, this.refreshSources);
    this.listenToRefreshes([]);
    this.generation++;
    if (this.observer) {
      this.observer.disconnect();
    }
  },
  methods: {
    /**
     * Collects the file's versions and the sources other add-ons registered,
     * the last one registered per id, that apply to the file, ordered by
     * rank, then shows the changes of all of them.
     * @returns {Promise} settled once the first changes are shown
     */
    loadSources() {
      const generation = ++this.generation;
      const params = {file: this.file, documentId: this.documentId};
      const byId = new Map();
      (extensionRegistry.loadComponents(EXTENSION_NAME) || [])
        .filter(component => component.componentName === SOURCE_TYPE && component.componentOptions.id !== VERSIONS_SOURCE)
        .forEach(component => byId.set(component.componentOptions.id, component.componentOptions));
      const registered = [...byId.values()];
      this.lines = [];
      this.loading = true;
      return Promise.all(registered.map(source => this.isSourceEnabled(source, params)))
        .then(enabled => {
          if (generation === this.generation) {
            this.sources = [this.versionsSource(), ...registered.filter((source, index) => enabled[index])]
              .sort((source1, source2) => Number(source1.rank || 0) - Number(source2.rank || 0));
            this.listenToRefreshes(this.sources);
            this.select(this.ALL);
          }
        });
    },
    /**
     * Reads the sources again when one is registered after the list opened,
     * while it shows no change yet, so that no line read is lost.
     * @returns {Promise} settled once the first changes are shown, or at once
     *          when changes already show
     */
    refreshSources() {
      return this.lines.length && Promise.resolve() || this.loadSources();
    },
    /**
     * Listens to the events after which the sources shown may hold new
     * changes, and to none of the sources shown before.
     * @param {Array<Object>} sources the sources shown
     * @returns {void}
     */
    listenToRefreshes(sources) {
      (this.refreshListeners || []).forEach(({event, listener}) => document.removeEventListener(event, listener));
      this.refreshListeners = sources.filter(source => source.refreshEvent).map(source => {
        const listener = event => this.refreshFor(source, event);
        document.addEventListener(source.refreshEvent, listener);
        return {event: source.refreshEvent, listener};
      });
    },
    /**
     * Shows the changes again, from the newest, with the same choice, when an
     * event a source listens to concerns the file shown.
     * @param {Object} source the source the event is for
     * @param {CustomEvent} event the event
     * @returns {Promise} settled once the first changes are shown again
     */
    refreshFor(source, event) {
      const params = {file: this.file, documentId: this.documentId};
      let concerned = true;
      try {
        concerned = !source.isRefreshFor || !!source.isRefreshFor(event?.detail, params);
      } catch (error) {
        concerned = false;
      }
      return concerned && this.select(this.selected) || Promise.resolve();
    },
    /**
     * @param {Object} source the options a source was registered with
     * @param {Object} params the file and the id of the file whose changes show
     * @returns {Promise<boolean>} whether the source applies to the file; not when its check fails
     */
    isSourceEnabled(source, params) {
      if (!source.isEnabled) {
        return Promise.resolve(true);
      }
      return Promise.resolve()
        .then(() => source.isEnabled(params))
        .then(enabled => !!enabled)
        .catch(() => false);
    },
    /**
     * The file's own versions, read at once: the server gives them all in
     * one answer, so the first page holds every one and no cursor follows.
     * A file never versioned has none: the server refuses the request for it.
     * The versions REST writes a date as an object holding its time in
     * milliseconds, as the version card reads it.
     * @returns {Object} the source of the file's versions, each keyed by its
     *          number and dated by its creation
     */
    versionsSource() {
      return {
        id: VERSIONS_SOURCE,
        labelKey: 'documents.drawer.details.versions',
        rank: 0,
        vueComponent: Vue.options.components['document-info-changes-version-line'],
        load: ({before}) => {
          if (before || !this.documentId || !this.file?.versionable) {
            return Promise.resolve({entries: [], next: null});
          }
          return this.$documentFileService.getFileVersions(this.documentId)
            .then(result => ({
              entries: (result?.versions || [])
                .map(version => ({time: versionTime(version), key: version.versionNumber, version}))
                .sort((entry1, entry2) => entry2.time - entry1.time || entry2.key - entry1.key),
              next: null,
            }));
        },
      };
    },
    /**
     * Shows, from the newest, the changes of the chosen source, of all of
     * them for « All ».
     * @param {string} sourceId the id of the chosen source, or all
     * @returns {Promise} settled once the first changes are shown
     */
    select(sourceId) {
      this.selected = sourceId || this.ALL;
      const chosen = this.selected === this.ALL && this.sources
        || this.sources.filter(source => source.id === this.selected);
      const file = this.file;
      const documentId = this.documentId;
      this.generation++;
      this.timeline = createTimeline(chosen.map(source => ({
        id: source.id,
        rank: Number(source.rank || 0),
        load: ({before, limit}) => source.load({file, documentId, before, limit}),
      })), PAGE_SIZE);
      this.lines = [];
      this.hasMore = true;
      this.failed = false;
      this.loading = false;
      return this.loadMore();
    },
    /**
     * Shows the following changes, then more while the end of the list stays
     * in view, since scrolling cannot reveal it again.
     * @returns {Promise} settled once they are shown, or dropped when the
     *          choice or the file changed meanwhile
     */
    async loadMore() {
      if (this.loading || !this.hasMore || !this.timeline) {
        return;
      }
      const generation = this.generation;
      const timeline = this.timeline;
      this.loading = true;
      const lines = await timeline.next(PAGE_SIZE);
      if (generation !== this.generation) {
        return;
      }
      this.lines.push(...lines);
      this.hasMore = timeline.hasMore();
      this.failed = timeline.hasFailed();
      this.loading = false;
      // the end marker is measured once the new lines are rendered
      await this.$nextTick();
      if (this.isEndInView()) {
        await this.loadMore();
      }
    },
    /**
     * @returns {boolean} whether the end of the list shows in the window
     */
    isEndInView() {
      const end = this.$refs.end;
      if (!end) {
        return false;
      }
      const bounds = end.getBoundingClientRect();
      return bounds.top < window.innerHeight && bounds.bottom >= 0;
    },
    /**
     * @param {Object} source a source
     * @returns {string} its label, translated when it gives a key
     */
    sourceLabel(source) {
      return source.labelKey && this.$te(source.labelKey) && this.$t(source.labelKey) || source.label || source.id;
    },
    /**
     * @param {string} sourceId a source's id
     * @returns {Object} the component showing one of its changes
     */
    sourceComponent(sourceId) {
      const source = this.sources.find(candidate => candidate.id === sourceId);
      return source && source.vueComponent;
    },
  },
};
</script>
