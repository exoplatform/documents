<template>
  <exo-drawer
    ref="documentInfoDrawer"
    v-model="drawer"
    :expanded="openExpanded"
    class="documentInfoDrawer"
    allow-expand
    right
    @expand-updated="updateExpanded"
    @closed="handleClosed">
    <template #title>
      <div
        v-if="sideBySide && file"
        :title="file.name"
        class="d-flex align-center text-truncate">
        <v-icon
          :class="icon.class"
          :color="icon.color"
          size="20"
          class="me-2" />
        <span class="text-truncate">{{ file.name }}</span>
      </div>
      <template v-else>
        {{ $t('documents.drawer.details.title') }}
      </template>
    </template>
    <template v-if="drawer && file" #titleIcons>
      <v-btn
        v-if="openLabel && sideBySide"
        class="primary me-2"
        depressed
        small
        @click="openPreview">
        {{ openLabel }}
      </v-btn>
      <documents-favorite-button
        v-if="!file.folder"
        :id="fileId"
        :file="file"
        :small="false"
        @added="favoriteAdded"
        @removed="favoriteRemoved" />
      <v-tooltip
        v-if="!$downloadDocumentSuspended"
        bottom>
        <template #activator="{on, bind}">
          <v-btn
            :href="downloadUrl"
            :aria-label="$t('documents.label.download')"
            icon
            v-on="on"
            v-bind="bind"
            download>
            <v-icon size="18">fas fa-download</v-icon>
          </v-btn>
        </template>
        <span>{{ $t('documents.label.download') }}</span>
      </v-tooltip>
      <document-info-menu
        :file="file"
        :transcript-available="!!mediaKind && transcriptAvailable && !sideBySide"
        @transcript="showTranscript"
        @copy-link="copyLink" />
    </template>
    <template v-if="drawer && file" #content>
      <div :class="sideBySide && 'd-flex fill-height'">
        <div v-if="sideBySide" class="documentInfoContentPane light-grey-background-color flex-grow-1 fill-height">
          <document-info-content
            ref="content"
            :file="file"
            :icon="icon"
            :media-kind="mediaKind"
            :download-url="downloadUrl"
            :readable="isFileReadable"
            :start="transcriptMoment || 0"
            :autoplay="transcriptPlaying"
            :subtitle="compactSubtitle"
            @time="playerSeconds = $event"
            @open="openPreview" />
        </div>
        <div :class="sideBySide && 'documentInfoSidePane d-flex flex-column fill-height'">
          <v-tabs
            v-if="sideBySide"
            v-model="tab"
            class="documentInfoTabs flex-grow-0">
            <v-tab href="#details">{{ $t('documents.drawer.details.tab.details') }}</v-tab>
            <v-tab v-if="!file.folder" href="#versions">{{ $t('documents.drawer.details.versions') }}</v-tab>
            <v-tab v-if="mediaKind && (transcriptAvailable || tab === 'transcript')" href="#transcript">{{ $t('documents.drawer.details.transcript') }}</v-tab>
          </v-tabs>
          <div
            v-show="!sideBySide || tab === 'details'"
            :class="sideBySide && 'overflow-y-auto'"
            class="px-4 pt-3 pb-4">
            <template v-if="!sideBySide">
              <template v-if="hasPreview">
                <div class="d-flex align-start mb-3">
                  <v-icon
                    :class="icon.class"
                    :color="icon.color"
                    size="20"
                    class="me-2 mt-1" />
                  <div :title="file.name" class="documentInfoName font-weight-bold">{{ file.name }}</div>
                </div>
                <v-card
                  :loading="loading"
                  :title="$t('documents.drawer.details.clickToOpen')"
                  class="documentInfoPreview overflow-hidden d-flex mb-2"
                  outlined
                  @click="openPreview">
                  <document-media-thumbnail
                    v-if="mediaKind"
                    :src="downloadUrl"
                    :kind="mediaKind"
                    :icon="icon"
                    @duration="readMediaDuration"
                    @loaded="loading = false"
                    @failed="handleMediaError" />
                  <img
                    v-else
                    :src="thumbnailUrl"
                    :alt="file.name"
                    class="ma-auto"
                    @load="handleThumbnailLoaded"
                    @error="handleError">
                </v-card>
              </template>
              <v-card
                v-else
                :title="$t('documents.drawer.details.clickToOpen')"
                class="documentInfoCompactHeader d-flex align-center pa-3 mb-2"
                outlined
                @click="openPreview">
                <v-avatar
                  size="52"
                  class="flex-shrink-0 light-grey-background-color"
                  rounded>
                  <v-icon
                    :class="icon.class"
                    :color="icon.color"
                    size="28" />
                </v-avatar>
                <div class="ms-3 overflow-hidden text-start">
                  <div :title="file.name" class="documentInfoName font-weight-bold">{{ file.name }}</div>
                  <div class="text-sub-title text-truncate">{{ compactSubtitle }}</div>
                </div>
              </v-card>
            </template>
            <document-info-details
              :file="file"
              :icon="icon">
              <template #about>
                <document-recording-details
                  v-if="mediaKind"
                  :document-id="fileId"
                  :mimetype="file.mimeType"
                  :measured-duration="mediaDuration"
                  @available="transcriptAvailable = $event"
                  @transcript="showTranscript" />
                <!-- Rows other add-ons contribute to a file's details, each
                     deciding from the file whether it shows. A name of its
                     own and strict-type: a non-strict slot renders every
                     component registered under its name, whatever the type,
                     and DocumentsInfo also holds the menu's contributions. -->
                <extension-registry-components
                  :params="detailsContributionParams"
                  name="DocumentsInfoDetails"
                  type="documents-info-details"
                  strict-type
                  parent-element="div"
                  element="div"
                  class="documentInfoDetailsContributions" />
              </template>
            </document-info-details>
            <div class="documentInfoGroupTitle">{{ $t('documents.drawer.details.category') }}</div>
            <document-categories :file="file" />
            <div class="documentInfoGroupTitle">{{ $t('documents.drawer.details.description') }}</div>
            <div v-if="showNoDescription" class="d-flex documentDescription">
              <v-icon size="20" class="descriptionIcon"> fas fa-file-alt </v-icon>
              <a
                v-if="file.acl.canEdit"
                class="font-weight-bold ps-4 clickable py-auto documentDescription"
                @click="openEditor">
                <span>{{ $t('documents.message.addYourDescription') }}</span>
              </a>
            </div>
            <v-hover>
              <div class="documentDescription" slot-scope="{ hover }">
                <v-row class="px-4">
                  <v-col class="px-0 py-0">
                    <div
                      v-show="showDescription"
                      :data-text="placeholder"
                      class="infoDescriptionToShow"
                      :hover="hover"
                      v-sanitized-html="file.description">
                      {{ placeholder }}
                    </div>
                  </v-col>
                  <v-col class="col-1 px-0 py-0">
                    <v-tooltip :disabled="isMobile" bottom>
                      <template #activator="{ on, attrs }">
                        <v-icon
                          v-show="showDescription && (hover || isMobile)"
                          v-bind="attrs"
                          v-on="on"
                          class="primary--text"
                          size="20"
                          @click="openEditor">
                          {{ 'fa fa-edit' }}
                        </v-icon>
                      </template>
                      <span> {{ $t('documents.drawer.details.description.edit') }} </span>
                    </v-tooltip>
                  </v-col>
                </v-row>
              </div>
            </v-hover>
            <div v-if="displayEditor" class="documentDescription d-flex flex-row">
              <rich-editor
                id="descriptionMessageInput"
                ref="descriptionMessage"
                v-model="file.description"
                :max-length="1300"
                :placeholder="$t('documents.alert.descriptionLimit')"
                autofocus
                class="flex" />
            </div>
          </div>
          <document-info-versions
            v-if="sideBySide && tab === 'versions'"
            :file="file"
            @restored="refreshFile"
            class="px-4 overflow-y-auto" />
          <document-info-transcript
            v-if="sideBySide && tab === 'transcript'"
            :document-id="fileId"
            :playing-seconds="playerSeconds"
            :seconds="transcriptMoment"
            :initial-term="transcriptTerm"
            class="px-4 overflow-y-auto"
            @term="transcriptTerm = $event"
            @seek="seek" />
        </div>
      </div>
    </template>
    <template v-if="drawer" #footer>
      <div v-if="displayEditor" class="d-flex">
        <v-spacer />
        <v-btn
          id="saveDescriptionButton"
          :loading="savingDescription"
          :disabled="disableButton"
          depressed
          class="primary btn no-box-shadow ms-auto"
          @click="updateDescription">
          {{ $t('documents.label.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
import {mediaKind} from '../js/RecordingLength.js';
import {isBlankImage} from '../js/BlankImage.js';

export default {
  props: {
    selectedView: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    drawer: false,
    expanded: false,
    tab: 'details',
    thumbnailUrl: null,
    playerSeconds: null,
    openExpanded: false,
    transcriptMoment: null,
    transcriptPlaying: false,
    transcriptTerm: '',
    origin: null,
    returning: false,
    clickUnderDrawer: false,
    narrowScroll: null,
    file: null,
    fileName: null,
    displayEditor: false,
    showNoDescription: false,
    showDescription: false,
    loading: true,
    firstCreateDescription: false,
    fileInitialDescription: '',
    isFavorite: false,
    showIcon: false,
    mediaDuration: null,
    mediaFailed: false,
    transcriptAvailable: false,
    pressedInside: false,
  }),
  computed: {
    mediaKind() {
      return this.file && !this.file.folder && mediaKind(this.file.mimeType) || null;
    },
    fileId() {
      return this.file?.id;
    },
    /**
     * What the details rows contributed by other add-ons receive: the file,
     * its identifier and its media type.
     * @returns {Object} the props of each contributed row
     */
    detailsContributionParams() {
      return {
        file: this.file,
        documentId: this.fileId,
        mimetype: this.file?.mimeType,
      };
    },
    modifiedDate() {
      return this.file?.modifiedDate;
    },
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    icon(){
      const fileIcon =  this.$documentsIconsExtension[0]?.get(this.file?.mimeType);
      return fileIcon ? fileIcon : this.file.folder ? this.$documentsIconsExtension[0]?.get('folder') : this.$documentsIconsExtension[0]?.get('file');
    },
    lastUpdated() {
      return this.file && (this.modifiedDate || this.file.createdDate) || '';
    },
    disableButton() {
      return this.file?.description && this.file?.description.replace( /(<([^>]+)>)/ig, '').length>1300
      || this.file?.description === this.fileInitialDescription
      || (!this.file?.description && !this.fileInitialDescription);
    },
    isFileEditable() {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === this.file.mimeType ).length > 0;
    },
    isFileReadable() {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === this.file.mimeType).length > 0;
    },
    downloadUrl() {
      return this.$documentsUtils.getDownloadUrl(this.fileId, this.modifiedDate);
    },
    /**
     * Lays the content beside the tabs once the drawer is expanded on a wide
     * screen.
     * @returns {boolean} true when the drawer shows content and tabs side by side
     */
    sideBySide() {
      return this.expanded && !this.$vuetify.breakpoint.smAndDown;
    },
    /**
     * Keeps the large preview for a file that has one: a video frame, or a
     * thumbnail the server renders and the browser loads; any other file gets
     * the compact header.
     * @returns {boolean} true when the large preview shows
     */
    hasPreview() {
      if (!this.file || this.file.folder) {
        return false;
      } else if (this.mediaKind) {
        return this.mediaKind === 'video' && !this.mediaFailed;
      }
      return !!this.thumbnailUrl && !this.showIcon;
    },
    /**
     * Names the header's open action as Documents opens the file: Edit in
     * the online editor when the viewer may edit it and the editor can, else
     * View, in the viewer or the preview; none for a folder, or for an audio
     * or video file, whose player is the content. Shown in the full view
     * only: the narrow drawer's preview or card opens the file on click.
     * @returns {string} the label, or null when there is no action
     */
    openLabel() {
      if (!this.file || this.file.folder || this.mediaKind) {
        return null;
      }
      return this.isFileEditable && this.file.acl?.canEdit && this.$t('documents.drawer.details.edit') || this.$t('documents.drawer.details.view');
    },
    /**
     * Sums up the file in the compact header: its type, and its size.
     * @returns {string} the subtitle, as « PDF · 2 MB »
     */
    compactSubtitle() {
      const type = this.$t(`documents.label.type.${this.icon?.type}`);
      if (this.file.folder) {
        return type;
      }
      const size = this.$documentsUtils.getSize(this.file.size);
      return `${type} · ${size.value} ${this.$t(`document.size.label.unit.${size.unit}`)}`;
    },
  },
  watch: {
    showDescription() {
      this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
    },
    displayEditor() {
      this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
    }
  },
  created() {
    this.$root.$on('open-folder', this.openFolder);
    document.addEventListener('open-info-drawer', this.open);
    document.addEventListener('document-views-updated', this.handleUpdateViews);
    document.addEventListener('search-metadata-tag', this.close);
    document.addEventListener('document-category-selected', this.close);
    document.addEventListener('mousedown', this.notePress, true);
    document.addEventListener('click', this.closeOnClickOutside);
    window.addEventListener('click', this.noteClickUnderDrawer, true);
    document.addEventListener('documents-transcript-expand', this.expandFromTranscript);
    document.addEventListener('closeDisplayedDrawer', this.collapseOnMaskOrEscape, true);
  },
  methods: {
    /**
     * Follows the drawer's expansion. A full view the transcript drawer opened
     * from these details expanded in place collapses to the narrow details as
     * they were, scrolled where they were. Any other collapse onto these
     * details stops the recording where it was, so expanding again opens it
     * there, paused. Collapsing the full view opened from
     * the transcript drawer closes it onto that drawer, still open underneath
     * with its mask: the moment and the search go back to it, and the full
     * view's player pauses. Opened from the details, it collapses to them,
     * narrow.
     * @param {boolean} expanded whether the drawer is expanded
     * @returns {void}
     */
    updateExpanded(expanded) {
      this.expanded = expanded;
      if (!expanded && this.origin !== 'transcript') {
        this.keepRecordingMoment();
      }
      if (!expanded && this.narrowScroll !== null) {
        const scroll = this.narrowScroll;
        this.narrowScroll = null;
        this.tab = 'details';
        // the narrow layout renders on the next tick, then takes its scroll back
        this.$nextTick(() => {
          const content = this.drawerContent();
          if (content) {
            content.scrollTop = scroll;
          }
        });
      }
      if (!expanded && this.origin === 'transcript' && this.fileId) {
        // synced now: the full view's player leaves the page with the re-render
        this.syncTranscript();
        this.returning = 'synced';
        this.close();
      }
    },
    /**
     * Keeps the moment the full view's recording reached, paused, so that
     * expanding again opens it there rather than playing from an older one.
     * Read when the full view collapses, before its player leaves the page
     * with the re-render.
     * @returns {void}
     */
    keepRecordingMoment() {
      const media = this.contentMedia();
      const moment = media ? media.currentTime : (this.playerSeconds ?? this.transcriptMoment);
      media?.pause?.();
      this.transcriptMoment = Number.isFinite(moment) ? Math.floor(moment) : null;
      this.transcriptPlaying = false;
    },
    /**
     * The full view's audio or video element.
     * @returns {HTMLMediaElement} the element, or null when the full view
     *          shows no recording
     */
    contentMedia() {
      return this.$refs.content?.$refs?.player?.$refs?.player || null;
    },
    /**
     * Gives the transcript drawer underneath the moment and the search the
     * full view reached, and whether it was playing; then pauses the full
     * view's player.
     * @returns {void}
     */
    syncTranscript() {
      const media = this.contentMedia();
      const playing = !!media && !media.paused;
      const moment = media ? media.currentTime : (this.playerSeconds ?? this.transcriptMoment);
      media?.pause?.();
      document.dispatchEvent(new CustomEvent('documents-transcript-sync', {detail: {
        documentId: this.fileId,
        seconds: Number.isFinite(moment) ? Math.floor(moment) : null,
        term: this.transcriptTerm,
        playing,
      }}));
    },
    /**
     * Before the drawer forgets the file: opened from the transcript drawer,
     * a collapse, a click on the mask or Esc returns to that drawer with the
     * moment and the search, while the close button leaves both.
     * @returns {void}
     */
    handleClosed() {
      if (this.origin === 'transcript' && this.fileId) {
        if (this.returning === true) {
          this.syncTranscript();
        } else if (!this.returning) {
          this.contentMedia()?.pause?.();
          document.dispatchEvent(new CustomEvent('documents-transcript-close', {detail: {documentId: this.fileId}}));
        }
      }
      this.returning = false;
      this.close();
    },
    /**
     * Treats a click on the mask or Esc, which close the top drawer, as a
     * collapse when this drawer is that drawer and was opened from the
     * transcript drawer.
     * @returns {void}
     */
    collapseOnMaskOrEscape() {
      const openedDrawers = eXo.openedDrawers || [];
      if (this.drawer && this.origin === 'transcript' && openedDrawers[openedDrawers.length - 1] === this.$refs.documentInfoDrawer) {
        this.returning = true;
      }
    },
    /**
     * Closes the drawer on a click outside it. A link opened by script, as a
     * version in a new tab, and a click in a dialog over the drawer, as the
     * preview of a version, are not a click outside: the drawer stays. The
     * click's path is read as it was dispatched, since closing a preview
     * removes the clicked button from the page before the click reaches here.
     * A click in a menu opened from the drawer, as a transcript speaker's
     * person search and its suggestions, is not a click outside either, nor a
     * click pressed inside: a list that goes away on the press, as the person
     * search's « No one found », leaves the click to the page itself.
     * @param {MouseEvent} event a click anywhere in the page
     * @returns {void}
     */
    closeOnClickOutside(event) {
      const pressedInside = this.pressedInside;
      this.pressedInside = false;
      if (this.clickUnderDrawer) {
        this.clickUnderDrawer = false;
        return;
      } else if (!event.isTrusted || pressedInside) {
        return;
      }
      if (!this.isInside(event)) {
        this.close();
      }
    },
    /**
     * Notes whether a press starts inside the drawer, before the click it
     * leads to: what was pressed may be gone from the page by then. Only the
     * main button's press leads to a click; another one's is never noted, so
     * that it cannot spare the next click, such as a key on a button.
     * @param {MouseEvent} event a press anywhere in the page
     * @returns {void}
     */
    notePress(event) {
      this.pressedInside = event.isTrusted && event.button === 0 && this.isInside(event);
    },
    /**
     * Tells whether an event happened in the drawer, in what it shows over
     * itself (its menus, its description editor, its categories, a dialog), or
     * in a menu one of its components opened.
     * @param {Event} event a press or a click
     * @returns {boolean} true when the event's path goes through one of them
     */
    isInside(event) {
      const path = event.composedPath?.() || [];
      return path.some(element => element.classList && (['documentInfoDrawer', 'documentDescription', 'documentCategories', 'documentInfoMenu', 'v-dialog__content'].some(name => element.classList.contains(name))
          || this.isMenuOpenedHere(element)));
    },
    /**
     * Tells whether an element is the content of a menu one of this drawer's
     * components opened. Vuetify renders a menu's content at the application's
     * root, outside the drawer, and every menu of the page has the same class:
     * the menu is the drawer's when its component is among the drawer's.
     * @param {Element} element an element of a click's path
     * @returns {boolean} true for the content of a menu opened from the drawer
     */
    isMenuOpenedHere(element) {
      if (!element.classList.contains('v-menu__content')) {
        return false;
      }
      let component = element.__vue__;
      while (component && component !== this) {
        component = component.$parent;
      }
      return !!component;
    },
    /**
     * Notes, before the click is handled, whether another drawer is open above
     * this one, as the transcript drawer opened from the details: a click in
     * it, on its close button or on the mask is that drawer's, and leaves the
     * details open underneath as they were. Read now, since the drawer it
     * closes has left the opened drawers by the time the click reaches the
     * page.
     * @returns {void}
     */
    noteClickUnderDrawer() {
      const openedDrawers = eXo.openedDrawers || [];
      const index = openedDrawers.indexOf(this.$refs.documentInfoDrawer);
      this.clickUnderDrawer = this.drawer && index >= 0 && index < openedDrawers.length - 1;
    },
    /**
     * Expanding the transcript drawer opened from these details expands them
     * in place, on the Transcript tab, at the moment the recording reached and
     * with the same search, playing on when the drawer played; the transcript
     * drawer then closes. The narrow details' scroll is kept for the collapse.
     * The event is marked handled for the transcript drawer.
     * @param {CustomEvent} event its detail holds documentId, seconds, term
     *          and playing
     * @returns {void}
     */
    expandFromTranscript(event) {
      const detail = event?.detail || {};
      if (!this.drawer || !this.fileId || detail.documentId !== this.fileId) {
        return;
      }
      event.preventDefault();
      this.transcriptMoment = Number.isFinite(detail.seconds) ? detail.seconds : null;
      this.transcriptPlaying = !!detail.playing;
      this.transcriptTerm = detail.term || '';
      this.playerSeconds = null;
      this.tab = 'transcript';
      if (!this.expanded) {
        this.narrowScroll = this.drawerContent()?.scrollTop || 0;
        this.$refs.documentInfoDrawer.toogleExpand();
      }
    },
    /**
     * The drawer's scrolling content.
     * @returns {HTMLElement} the element, or null before the drawer renders
     */
    drawerContent() {
      return this.$refs.documentInfoDrawer?.$el?.querySelector?.('.drawerContent') || null;
    },
    /**
     * Falls back to the compact header when the browser cannot load the
     * file's thumbnail.
     * @returns {void}
     */
    handleError() {
      this.showIcon=true;
      this.loading = false;
    },
    /**
     * Falls back to the compact header when the page the server rendered for
     * an office document or a PDF is blank, as for a document it could not
     * render; an image keeps its preview, however pale.
     * @param {Event} event the image's load event
     * @returns {void}
     */
    handleThumbnailLoaded(event) {
      this.loading = false;
      if (this.isFileReadable && isBlankImage(event.target)) {
        this.showIcon = true;
      }
    },
    /**
     * Keeps how long the audio or video lasts, read from its metadata.
     * @param {Number} duration the duration in seconds, or null when unknown
     * @returns {void}
     */
    readMediaDuration(duration) {
      this.mediaDuration = duration;
      this.loading = false;
    },
    /**
     * Shows the file's icon instead of a frame of a video the browser could
     * not load.
     * @returns {void}
     */
    handleMediaError() {
      this.mediaFailed = true;
      this.showIcon = true;
      this.loading = false;
    },
    /**
     * Builds the url of the file's thumbnail, and marks the file as having
     * none when the server renders no thumbnail for its type.
     * @returns {string} the thumbnail's url, or null
     */
    getThumbnailUrl() {
      if (this.file.folder) {return null;}
      const file = this.file;
      file.readable = this.isFileReadable;
      const thumb = this.$documentsUtils.getThumbnailUrl(file,'250x250',this.lastUpdated);
      if (!thumb) {
        this.loading = false;
        this.showIcon = true;
      }
      return thumb;
    },
    /**
     * Keeps the views count of the shown file up to date.
     * @param {CustomEvent} event its detail holds the file and its views
     * @returns {void}
     */
    handleUpdateViews(event) {
      if (this.file?.id === event.detail?.file?.id) {
        this.file.views = event.detail?.views;
      }
    },
    /**
     * Saves the description being edited.
     * @returns {Promise} resolved once saved
     */
    updateDescription(){
      if (this.firstCreateDescription){
        this.addDescriptionStatistics(this.file);
      } else {
        this.updateDescriptionStatistics(this.file);
      }
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      return this.$documentFileService.updateDescription(ownerId,this.file)
        .then(() => {
          if (this.isMobile){
            this.displayAlert(this.$t('documents.alert.success.description.updated'));
          } else {
            this.$root.$emit('alert-message',  this.$t('documents.alert.success.description.updated'), 'success');
          }
          this.showDescription = this.file.description && this.file.description.length;
          this.showNoDescription = !this.file.description;
          this.displayEditor=false;
          this.fileInitialDescription = this.file.description;
          this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
        }).catch(() => {
          this.$root.$emit('alert-message',  this.$t('documents.alert.error.description.updated'), 'error');
        });
    },
    /**
     * Reads the file the event names, with its creator, modifier, owner and
     * metadata, and opens the drawer on it: narrow, on its details, unless
     * the event asks for the full view on a tab, at a moment of the recording
     * and with a search, as the transcript drawer does when expanded.
     * @param {CustomEvent} event its detail is the file's id, or an object
     *          with documentId, and optionally expanded, tab, seconds and term
     * @returns {void}
     */
    open(event) {
      const request = event?.detail && typeof event.detail === 'object' && event.detail || {documentId: event?.detail};
      const fileId = request.documentId;
      const expand =  'modifier,creator,owner,metadatas';
      this.$attachmentService.getDocumentDetails(fileId,expand)
        .then(file => {
          this.file = file;
          this.icon = file.icon;
          this.displayEditor = false;
          this.showIcon = false;
          this.mediaDuration = null;
          this.mediaFailed = false;
          this.transcriptAvailable = false;
          if (this.mediaKind === 'audio') {
            this.loading = false;
          }
          this.showNoDescription = !this.file.description && !this.displayEditor;
          this.showDescription = this.file.description && this.file.description.length && !this.displayEditor;
          this.fileInitialDescription = this.file.description;    
          this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length; 
          this.tab = request.tab || 'details';
          this.openExpanded = !!request.expanded;
          this.transcriptMoment = Number.isFinite(request.seconds) ? request.seconds : null;
          this.transcriptPlaying = !!request.playing;
          this.transcriptTerm = request.term || '';
          this.origin = request.origin || null;
          this.playerSeconds = null;
          if (file.folder){
            this.file.mimeType = 'folder';
            this.showIcon = true;
            this.loading = false;
            this.thumbnailUrl = null;
          } else {
            this.thumbnailUrl = this.mediaKind ? null : this.getThumbnailUrl();
          }
          this.$nextTick(()=>{
            this.$refs.documentInfoDrawer.open();
          });
        });

    },
    /**
     * Shows the description editor.
     * @returns {void}
     */
    openEditor(){
      this.firstCreateDescription = this.showNoDescription;
      this.showNoDescription = false;
      this.showDescription = false;
      this.displayEditor=true;
      this.$nextTick(()=>{
        this.$refs.descriptionMessage?.initCKEditorData(this.file.description);
        document.getElementById('descriptionMessageInput').scrollIntoView();
      });
    },
    /**
     * Closes the drawer and forgets the file.
     * @returns {void}
     */
    close() {
      this.origin = null;
      this.narrowScroll = null;
      this.transcriptPlaying = false;
      this.file = null;
      this.displayEditor = false;
      this.showNoDescription = false;
      this.showDescription = true;
      this.showIcon = false;
      this.mediaDuration = null;
      this.mediaFailed = false;
      this.transcriptAvailable = false;
      this.loading = true;
      this.$refs.documentInfoDrawer.close();
    },
    /**
     * Shows a message in the attachments' alert area, used on mobile.
     * @param {string} message the message
     * @param {string} type success or error
     * @returns {void}
     */
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('attachments-notification-alert', {
        detail: {
          messageObject: {
            message: message,
            type: type || 'success',
          }
        }
      }));
    },
    /**
     * Records that a description was added.
     * @param {Object} file the file
     * @returns {void}
     */
    addDescriptionStatistics(file) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionCreateDescription',
          operation: 'createDescription',
          parameters: {
            documentName: file.name,
            category: this.file.folder ? 'folderCategory' : 'documentCategory',
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    /**
     * Records that a description was changed.
     * @param {Object} file the file
     * @returns {void}
     */
    updateDescriptionStatistics(file) {
      document.dispatchEvent(new CustomEvent('exo-statistic-message', {
        detail: {
          module: 'Drive',
          subModule: 'Documents',
          userId: eXo.env.portal.userIdentityId,
          userName: eXo.env.portal.userName,
          name: 'actionUpdateDescription',
          operation: 'updateDescription',
          parameters: {
            documentName: file.name,
            category: this.file.folder ? 'folderCategory' : 'documentCategory',
            spaceId: eXo.env.portal.spaceId,
            view: this.selectedView === 'timeline' ? 'recentView': 'folderView',
          },
          timestamp: Date.now()
        }
      }));
    },
    /**
     * Opens a folder in the Documents application.
     * @param {Object} folder the folder, with its path
     * @returns {void}
     */
    openFolder(folder) {
      let folderPath = '';
      const pathParts = folder.path.split('/');
      const spaceIndex = pathParts.indexOf('spaces');
      if (spaceIndex !== -1){
        if (pathParts[spaceIndex+2]=== 'Documents'){
          pathParts[spaceIndex+2] = 'documents';
        }
        folderPath = pathParts.slice(spaceIndex + 1, pathParts.length ).join('/');
        folderPath = `${eXo.env.portal.context}/g/:spaces:${folderPath}`;          
      } else if (pathParts.indexOf('Users') !== -1){
        const parentIndex = pathParts.indexOf('Private');
        if (parentIndex!== -1){
          folderPath = pathParts.slice(parentIndex, pathParts.length ).join('/');
          folderPath = `${this.$documentsUtils.getPersonalDriveUrl()}/${folderPath}`;
        }
      }
      this.$documentsUtils.openLink(folderPath,'_self');
    },
    /**
     * Opens the file: a folder in Documents, an audio or video with its
     * transcript when it can be offered, an office file in its editor, anything
     * else in the preview.
     * @returns {void}
     */
    openPreview() {
      if (this.file.folder) {
        this.openFolder(this.file);
        return;
      }
      if (this.mediaKind && this.transcriptAvailable && !this.openPlayerWithTranscript()) {
        return;
      }
      this.loading = true;
      if (this.isFileEditable)  {
        if (this.file?.acl?.canEdit){
          this.openFileInEditor();
        } else {
          this.openFileInEditor('view');
        }
      } else if (this.isFileReadable)  {
        this.openFileInEditor('view');
      } else {
        const attachments = [];
        attachments.push({
          id: this.fileId,
          downloadUrl: this.downloadUrl,
          name: this.file.name,
          filename: this.file.name,
          mimetype: this.file.mimeType,
          icon: this.icon,
          editable: this.isFileEditable,
          readable: this.isFileReadable,
          path: this.file.path,
          source: 'documents'
        });
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': attachments,'id': this.fileId }}));
      }
      document.dispatchEvent(new CustomEvent('mark-attachment-as-viewed', {detail: {file: this.file}}));
      this.close();
      this.loading = false;
    },
    /**
     * Asks the transcript drawer to open the player of an audio or video file
     * with its transcript, as the header's transcript button does; called only
     * once the transcription state says the transcript can be offered. The
     * transcript drawer opens above these details, which stay open underneath.
     * @returns {boolean} true when nothing on the page answered, so the
     *          preview opens instead
     */
    openPlayerWithTranscript() {
      return document.dispatchEvent(new CustomEvent('documents-open-transcript', {
        detail: {
          documentId: this.fileId,
          origin: 'info',
        },
        cancelable: true,
      }));
    },
    /**
     * Opens the file in its editor, in a new tab.
     * @param {string} mode 'view' to open it read only
     * @returns {void}
     */
    openFileInEditor(mode) {
      if (this.file && this.fileId) {
        const url = this.$documentsUtils.getEditorUrl(this.file,mode);
        this.$documentsUtils.openLink(url,'_blank');
      }
    },
    /**
     * Reads the file again after a version was restored, so the header, the
     * preview and the details show its new current content; the drawer stays
     * open on the same view and tab.
     * @returns {Promise} resolved once read
     */
    refreshFile() {
      const fileId = this.fileId;
      return this.$attachmentService.getDocumentDetails(fileId, 'modifier,creator,owner,metadatas')
        .then(file => {
          if (fileId === this.fileId) {
            this.file = file;
            this.showIcon = false;
            this.thumbnailUrl = this.mediaKind ? null : this.getThumbnailUrl();
          }
        });
    },
    /**
     * Shows the transcript: in its tab once the drawer is expanded, else in
     * the transcript drawer, as the former header button did.
     * @returns {void}
     */
    showTranscript() {
      if (this.sideBySide) {
        this.tab = 'transcript';
      } else {
        this.openPlayerWithTranscript();
      }
    },
    /**
     * Moves the recording beside the transcript to a moment.
     * @param {number} seconds the moment, in seconds from the start
     * @returns {void}
     */
    seek(seconds) {
      this.$refs.content?.seek(seconds);
    },
    /**
     * Notes that the file left the favorites.
     * @returns {void}
     */
    favoriteRemoved() {
      this.isFavorite = false;
    },
    /**
     * Notes that the file joined the favorites.
     * @returns {void}
     */
    favoriteAdded() {
      this.isFavorite = true;
    },
    /**
     * Copies a link to the file: its editor when it has one, else its folder
     * with its preview open.
     * @returns {void}
     */
    copyLink() {
      this.loading = true;
      let path;
      if (this.isFileEditable)  {
        if (this.file?.acl?.canEdit){
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,null)}`;
        } else {
          path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
        }
      } else if (this.isFileReadable)  {
        path =  `${window.location.origin}${this.$documentsUtils.getEditorUrl(this.file,'view')}`;
      } else {
        path = `${window.location.origin}${this.$documentsUtils.getParentFolderUrl(this.file)}?documentPreviewId=${this.fileId}`;
      }
      const input = document.createElement('input');
      input.value = path;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      this.loading = false;
    },
  },
};
</script>
