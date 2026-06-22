<template>
  <div v-if="documentsBreadcrumbList.length" class="documents-breadcrumb-wrapper">
    <div class="documents-tree-items d-flex align-center">
      <v-btn
        v-if="!treeViewExpended || isMobile"
        icon
        v-bind="attrs"
        v-on="on"
        class="me-2 ms-n2"
        :disabled="disabledIconTree"
        @click.stop.prevent="openTreeView()">
        <img
          alt=""
          :title="$t('documents.tooltip.open.tree')"
          src="/social/images/sidebar.svg"
          class="icon-default-color pb-1"
          height="20px"
          width="20px">
      </v-btn>
      <div
        v-if="!isMobile"
        id="breadcrumb-list-items"
        data-isfolder="true"
        :data-fileId="documentsBreadcrumbToDisplay[0].id"
        :data-canEdit="canEditFile(documentsBreadcrumbToDisplay[0])? 'true': 'false'"
        class="d-flex align-center width-full pa-1 mb-1">
        <template v-for="(document, index) in documentsBreadcrumbList">
          <div
            v-if="document.isBreadcrumbItem && !document.isEllipsis"
            :key="index"
            :data-fileId="document.id"
            :class="document.class"
            class="document-breadcrumb-item">
            <v-tooltip max-width="300" bottom>
              <template #activator="{ on, attrs }">
                <a
                  :id="document.isLast && 'lastBreadcrumbItem'"
                  :ref="document.id"
                  :class="document.classLink"
                  v-bind="attrs"
                  v-on="on"
                  @click="!document.isLast && openFolder(documentsBreadcrumb[document.index])">{{ getName(document.name) }}</a>
              </template>
              <span class="caption breadcrumbName">
                {{ getName(document.name) }}
                <v-icon
                  v-if="document.symlink"
                  size="10"
                  class="pe-1 iconStyle">
                  mdi-link-variant
                </v-icon>
              </span>
            </v-tooltip>
            <v-icon
              v-if="document.isIcon"
              class="flex-grow-1 icon-default-color flex-shrink-0"
              :class="index === 0 && documentsEllipsisList.length > 0 ? 'ms-2' : 'mx-2'"
              size="14">
              fa-chevron-right
            </v-icon>
          </div>
          <div
            v-if="document.isEllipsis"
            :key="index"
            class="min-width-content long-path-second-item d-flex">
            <v-tooltip :key="tooltipKey" bottom>
              <template #activator="{ on, attrs }">
                <v-icon
                  v-show="documentsEllipsisList.length > 0"
                  v-bind="attrs"
                  v-on="on"
                  class="text-sub-title"
                  size="18"
                  @click="openFolder(documentsBreadcrumb[documentsEllipsisList[documentsEllipsisList.length-1].index])">
                  fas fa-ellipsis-h
                </v-icon>
              </template>
              <p
                v-for="(document) in documentsEllipsisList"
                :key="document.index"
                class="mb-0">
                <span
                  class="caption">
                  <v-icon
                    size="18"
                    class="tooltip-chevron">
                    fas fa-chevron-right
                  </v-icon>
                  {{ document.name }}
                </span>
              </p>
            </v-tooltip>
            <v-icon class="clickable me-2" size="18">fas fa-chevron-right</v-icon>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    documentsBreadcrumb: {
      type: Array,
      default: () => [],
    },
    showIcon: {
      type: Boolean,
      default: true,
    },
    move: {
      type: Boolean,
      default: false,
    },
    treeViewExpended: {
      type: String,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    id: Math.random().toString(16),
    actualFolderId: '',
    driveName: '',
    folderPath: '',
    currentFolderPath: '',
    showHidden: false,
    drives: [],
    documentsBreadcrumbList: [],
    documentsEllipsisList: [],
    isMounted: false,
    tooltipKey: 0,
    countRecursive: 0,
  }),
  computed: {
    documentsBreadcrumbToDisplay() {
      if (this.actualFolderId) {
        this.$root.$emit('documentsBreadcrumb',this.documentsBreadcrumb);
      }
      const maxItemsToDisplay = this.isMobile ? 2 : 4;
      const maxItemsCount = this.isMobile ? 2 : 3;
      if (!this.documentsBreadcrumb || this.documentsBreadcrumb.length <= maxItemsToDisplay) {
        return this.documentsBreadcrumb || [];
      } else {
        const length = this.documentsBreadcrumb.length;
        const documentsBreadcrumbToDisplay = [this.documentsBreadcrumb[0], ... this.documentsBreadcrumb.slice(length - maxItemsCount, length)];
        documentsBreadcrumbToDisplay[1] = Object.assign({}, documentsBreadcrumbToDisplay[1], {
          name: '...',
        });
        return documentsBreadcrumbToDisplay;
      }
    },
  },
  watch: {
    documentsBreadcrumb() {
      const drive = { ... this.documentsBreadcrumb[0]};
      if (drive.path?.startsWith?.('/Groups/spaces')) {
        drive.name = `.spaces.${drive.path.split('/').filter(s => s?.length)[2]}`;
      } else {
        drive.name='Personal Documents';
      }
      this.$root.selectedDrive = drive;
      this.$root.selectedPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length-1]?.path?.replace(`${drive.path}/`,'');
      if (this.$root.selectedPath?.indexOf('Public') !== -1) {
        this.$root.selectedPath = `Public/${this.$root.selectedPath?.split('Public')[1]}` || 'Public';
      }
      this.initDocumentsBreadcrumb();
      this.$forceUpdate();
      this.isMounted = true;
    },
    isMounted() {
      if (this.isMounted) {
        document.fonts.ready.then(() => {
          this.$nextTick(() => {
            if (this.documentsEllipsisList.length === 0 || this.documentsBreadcrumb.length > 4) {
              this.isLastDocumentsTruncated();
              this.countRecursive = 0;
            }
          });
        });
        this.isMounted = false;
      }
    },
  },
  created() {
    this.$root.$on('set-breadcrumb', this.setBreadcrumb);
    this.$root.$on('update-breadcrumb', this.updateBreadcrumb);
    this.$root.$on('open-folder', this.openFolder);
    document.addEventListener('document-open-previous-folder-to-drop', this.handleOpenRootFolder);
    document.addEventListener('move-dropped-documents-on-breadcrumb', this.handleMoveDroppedOnBreadcrumb);
    this.$root.$on('document-show-drives', this.showDrives);
    this.$root.$on('set-advanced-filter', advancedFilter => {
      this.showHidden = advancedFilter.showHidden;
    });
    this.$root.$on('add-drives', this.addDrives);
  },
  beforeDestroy() {
    this.$root.$off('set-breadcrumb', this.setBreadcrumb);
    this.$root.$off('update-breadcrumb', this.updateBreadcrumb);
    this.$root.$off('open-folder', this.openFolder);
    this.$root.$off('document-show-drives', this.showDrives);
    this.$root.$off('add-drives', this.addDrives());
    document.removeEventListener('document-open-previous-folder-to-drop', this.handleOpenRootFolder);
    document.removeEventListener('move-dropped-documents-on-breadcrumb', this.handleMoveDroppedOnBreadcrumb);
  },
  methods: {
    handleMoveDroppedOnBreadcrumb(event) {
      const indexLastItem = this.documentsBreadcrumbToDisplay.length - 1 > 0 ? this.documentsBreadcrumbToDisplay.length - 1 : 0;
      event.detail.currentOpenedFolder =  this.documentsBreadcrumbToDisplay[indexLastItem];
      document.dispatchEvent(new CustomEvent('move-dropped-documents', event));
    },
    handleOpenRootFolder() {
      if (this.documentsBreadcrumbToDisplay.length > 1) {
        this.$root.$emit('document-open-folder', this.documentsBreadcrumbToDisplay[this.documentsBreadcrumbToDisplay.length - 2]);
      }
    },
    addDrives(drives) {
      if (drives) {
        this.drives.push(...drives);
      }
    },
    canEditFile(file) {
      return file?.accessList?.canEdit;
    },
    showDrives() {
      this.documentsBreadcrumb = [];
      this.documentsBreadcrumb.push({
        id: 'space_drives',
        name: this.$t('documents.label.drives'),
        drives: true,
        symlink: false,
      });
    },
    setBreadcrumb(folder) {
      this.folderPath = '';
      if (folder) {
        if (folder.drive) {
          this.actualFolderId = '';
          this.driveName = folder.name;
          this.documentsBreadcrumb = [];
          this.documentsBreadcrumb.push({
            name: folder.name,
            symlink: false,
          });
        } else {
          this.actualFolderId = folder.id;
          this.getBreadCrumbs();
        }
      }
      this.$root.$emit('breadcrumb-updated');
    },
    openFolder(folder) {
      this.documentsBreadcrumb = [];
      this.documentsBreadcrumbList = [];
      if (folder.name === 'Private'){
        this.$root.$emit('document-open-home');
        this.folderPath='';
        this.actualFolderId ='';
        this.getBreadCrumbs();
      } else if (folder.drive) {
        this.actualFolderId = '';
        this.driveName = folder.name;
        this.documentsBreadcrumb = [];
        this.documentsBreadcrumb.push({
          name: folder.name,
          symlink: false,
        });
        this.$root.$emit('document-open-folder', folder);
      } else if (folder.id !== this.actualFolderId ) {
        this.folderPath='';
        this.actualFolderId=folder.id;
        this.getBreadCrumbs();
        this.$root.$emit('document-open-folder', folder);
      }
      this.$root.$emit('breadcrumb-updated');
    },
    getName(name){
      if (name==='Private'){
        return this.$t('documents.label.userHomeDocuments');
      } else if (name==='Documents'){
        if (this.$root.ownerId !== eXo.env.portal.userIdentityId){
          const drive = this.drives.find(drive => drive.identityId === this.$root.ownerId);
          if (drive) {
            return drive.name;
          }
        }
        return this.$t('documents.label.spaceHomeDocuments');
      }
      return name;
    },
    getDocumentDataFromUrl() {
      const currentUrlSearchParams = window.location.search;
      const queryParams = new URLSearchParams(currentUrlSearchParams);
      if (queryParams.has('path')) {
        const parentDriveFolder = eXo.env.portal.spaceName && '/Documents/' || '/Private/';
        const path = queryParams.get('path') || '';
        const nodePath = path.substring(path.indexOf(parentDriveFolder) + parentDriveFolder.length);
        const nodePathParts = nodePath.split('/');
        this.getFolderPath(nodePathParts.join('/'));
      } else if (queryParams.has('folderId')) {
        this.actualFolderId = queryParams.get('folderId');
        this.getBreadCrumbs();
      } else {
        this.getFolderPath();
      }
    },
    getFolderPath(path){
      if (path) {
        this.folderPath = path;
      } else {
        path = window.location.pathname;
        const pathParts  = path.toLowerCase().includes('/drives') || path.toLowerCase().includes('/documents')? path.split( `${eXo.env.portal.selectedNodeUri.toLowerCase()}/`) : [path];
        if (pathParts.length > 1) {
          this.folderPath = pathParts[1];
        } else {
          this.folderPath = '';
        }
        if (!eXo.env.portal.spaceName) {
          if (path.includes('/Private/')){
            const pathParts  = path.split('/Private');
            if (pathParts.length > 1){
              this.folderPath = `Private${pathParts[1]}`;
            }
          }
          if (path.includes('/Public')) {
            const pathParts  = path.split('/Public');
            if (pathParts.length > 1) {
              this.folderPath = `Public${pathParts[1]}`;
            }
          }
        }
      }
      this.actualFolderId = '';
      this.currentFolderPath = '';
      this.getBreadCrumbs();
    },

    getBreadCrumbs() {
      return this.$documentFileService
        .getBreadCrumbs(this.actualFolderId,this.$root.ownerId,this.folderPath)
        .then(breadCrumbs => {
          this.documentsBreadcrumb = breadCrumbs;
          this.actualFolderId = this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1]?.id;
          this.currentFolderPath = this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1]?.path;
          this.$root.$emit('set-current-folder', this.documentsBreadcrumb[this.documentsBreadcrumb.length - 1]);
        })
        .finally(() => this.loading = false);
    },
    updateBreadcrumb(folderPath) {
      if (folderPath) {
        this.getFolderPath(folderPath);
      } else {
        this.getDocumentDataFromUrl();
      }
    },
    openTreeView() {
      if (this.isMobile) {
        this.$root.$emit('documentsBreadcrumb',this.documentsBreadcrumb);
        this.$root.$emit('openTreeFolderDrawer',this.showHidden);
      } else {
        this.$root.$emit('tree-view-expend', true);
      }
    },
    initDocumentsBreadcrumb() {
      const documentsBreadcrumbLength = this.documentsBreadcrumb.length;
      this.documentsBreadcrumbList = [];
      this.documentsEllipsisList = [];
      this.documentsBreadcrumb.forEach((document, index) => {
        const isFirst = index === 0;
        const isLast = index === documentsBreadcrumbLength - 1;
        const isSecondLast = index === documentsBreadcrumbLength - 2;
        const isEllipsis = documentsBreadcrumbLength > 4 && this.documentsEllipsisList.length === 0 && !isFirst;
        const isBreadcrumbItem = isFirst || isLast || isSecondLast;
        const breadcrumbItem = {
          ...document,
          index,
          class: isLast ? 'd-flex text-truncate mx-2' : 'd-flex  document-tree-item min-width-content',
          classLink: isLast ? 'text-header text-color text-truncate breadCrumb-link' : 'text-header text-sub-title path-clickable breadCrumb-link',
          isIcon: isFirst && documentsBreadcrumbLength !== 1  || !isLast,
          isLast,
          isEllipsis,
          isBreadcrumbItem,
        };
        if (documentsBreadcrumbLength > 4 && !isFirst && !isSecondLast && !isLast) {
          this.documentsEllipsisList.push(breadcrumbItem);
        }
        this.documentsBreadcrumbList.push(breadcrumbItem);
      });
    },
    getTextWidth(text, font) {
      const canvas = document.createElement('canvas');
      const context = canvas.getContext('2d');
      context.font = font;
      return context.measureText(text).width;
    },
    isLastDocumentsTruncated() {
      document.fonts.ready.then(() => {
        this.$nextTick(() => {
          const documentsBreadcrumbListLength = this.documentsBreadcrumbList.length;
          if (this.countRecursive === 2){
            this.documentsBreadcrumbList[documentsBreadcrumbListLength - 1].class = 'd-flex text-truncate min-width-title';
            return;
          }
          const lastdocumentsElement = document.getElementById('lastBreadcrumbItem');
          if (!lastdocumentsElement) {
            return;
          }
          const elementWidth = lastdocumentsElement.clientWidth;
          const text = this.getTruncatedText(lastdocumentsElement);
          const font = window.getComputedStyle(lastdocumentsElement).font;
          const textWidth = this.getTextWidth(lastdocumentsElement.innerText, font);
          if (textWidth > elementWidth && text.length <= 10) {
            if (this.documentsBreadcrumb.length < 5 && this.countRecursive === 0) {
              this.getDocumentsBreadcrumbList();
            } else if (this.countRecursive === 0) {
              const beforeLastIndex = documentsBreadcrumbListLength - 2;
              const beforeLastElement = this.documentsBreadcrumbList[beforeLastIndex];
              beforeLastElement.isBreadcrumbItem = false;
              beforeLastElement.isIcon = false;
              beforeLastElement.isEllipsis = this.documentsEllipsisList.length === 0 ? true : false;
              this.documentsEllipsisList.push(beforeLastElement);
              this.documentsBreadcrumbList = this.documentsBreadcrumbList.map((item, index) => {
                return index === beforeLastIndex ? beforeLastElement : item;
              });
            } else if (this.countRecursive === 1) {
              this.documentsBreadcrumbList[0].class = 'd-flex document-tree-item min-width-content';
              this.documentsBreadcrumbList[0].classLink = 'text-header text-sub-title path-clickable breadCrumb-link';
            }
            this.isLastDocumentsTruncated();
            this.countRecursive += 1;
          } else {
            this.documentsBreadcrumbList[documentsBreadcrumbListLength - 1].class = 'd-flex text-truncate min-width-title';
          }
        });
      });
    },
    getTruncatedText(element) {
      const text = element.innerText;
      const style = window.getComputedStyle(element);
      const font = `${style.fontSize} ${style.fontFamily}`;
      const canvas = document.createElement('canvas');
      const context = canvas.getContext('2d');
      context.font = font;
      let truncatedText = '';
      const visibleWidth = element.clientWidth;
      for (let i = 0; i < text.length; i++) {
        const char = text[i];
        const charWidth = context.measureText(truncatedText + char).width;
        if (charWidth > visibleWidth - 400) {
          break;
        }
        truncatedText += char;
      }
      return truncatedText;
    },
    getDocumentsBreadcrumbList() {
      this.documentsBreadcrumbList = [];
      this.documentsEllipsisList = [];
      const documentsBreadcrumbLength = this.documentsBreadcrumb.length;
      this.documentsBreadcrumb.forEach((document, index) => {
        const isFirst = index === 0;
        const isLast = index === documentsBreadcrumbLength - 1;
        const isEllipsis = this.documentsEllipsisList.length === 0 && !isFirst && !isLast;
        const isBreadcrumbItem = isFirst || isLast;
        const breadcrumbItem = {
          ...document,
          index,
          class: isLast ? 'd-flex text-truncate' : 'd-flex document-tree-item min-width-content',
          classLink: isLast ? 'text-header text-color text-truncate breadCrumb-link' : 'text-header text-sub-title path-clickable breadCrumb-link',
          isIcon: isFirst,
          isLast,
          isEllipsis,
          isBreadcrumbItem,
        };
        if (!isFirst && !isLast) {
          this.documentsEllipsisList.push(breadcrumbItem);
        }
        this.documentsBreadcrumbList.push(breadcrumbItem);
      });
      this.tooltipKey += 1;
    },
  }
};
</script>
