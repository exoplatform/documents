<template>
  <div class="group-menu-action">
    <v-menu
      v-model="menuDisplayed"
      transition="slide-x-transition"
      content-class="documentActionMenu "
      offset-x
      close-on-click
      :nudge-right="16"
      :nudge-top="8">
      <template #activator="{ on, attrs }">
        <div
          dark
          icon
          v-bind="attrs"
          v-on="on"
          class="clickable ma-auto my-10px mx-2"
          @mousedown="disableLeftClick">
          <v-icon
            size="16"
            class="pe-1">
            {{ icon }}
          </v-icon>
          <span class="ps-1 ml-n2px text-body menu-text-color">{{ $t(labelKey) }}</span>

          <v-icon size="16" class="pull-right">fa-caret-right</v-icon>
        </div>
      </template>
      <documents-actions-menu
        :file="file"
        :current-view="currentView"
        :is-search-result="isSearchResult"
        :is-mobile="isMobile"
        :parent="id" />
    </v-menu>
  </div>
</template>
<script>

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
    currentView: {
      type: String,
      default: ''
    },
    isSearchResult: {
      type: Boolean,
      default: false
    },
    parent: {
      type: String,
      default: ''
    },
    id: {
      type: String,
      default: ''
    },
    labelKey: {
      type: String,
      default: ''
    },
    icon: {
      type: String,
      default: ''
    },
  },

  data: () => ({
    menuDisplayed: false,
    waitTimeUntilCloseMenu: 100
  }),
  created() {
    $(document).on('mousedown', () => {      
      if (this.menuDisplayed) {
        window.setTimeout(() => {
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
  },
  methods: {
    disableLeftClick(event) {
      if (event.button === 2) {
        event.preventDefault();
        event.stopPropagation();
      }
    }
  }
 
};
</script>
