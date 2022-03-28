<template>
  <div class="d-flex">
    <div class="profile-popover user-wrapper pl-4 pt-2 pb-1">
      <a
        class="d-flex not-clickable flex-nowrap flex-grow-1 text-truncate container--fluid align-center">
        <v-avatar
          size="32"
          class="ma-0">
          <img
            :src="avatarUrl"
            class="object-fit-cover ma-auto"
            loading="lazy"
            role="presentation">
        </v-avatar>
        <div v-if="displayName || $slots.subTitle" class="ms-2 overflow-hidden">
          <p
            v-if="displayName"
            class="text-truncate subtitle-2 text-color text-left mb-0 font-weight-bold">
            {{ displayName }}
          </p>
        </div>
      </a>
    </div>
    <v-spacer />
    <div
      class="my-auto d-flex pe-2">
      <v-icon
        v-if="userVisibility === 'view'"
        class="pb-2"
        :size="13">
        fas fa-eye
      </v-icon>
      <v-icon
        v-if="userVisibility !== 'view'"
        class="pb-2"
        :size="13">
        fas fa-edit
      </v-icon>
      <documents-visibility-menu
        @visibility-user="visibilityUser" />
      <v-divider vertical />
      <v-icon
        :title="$t('documents.label.visibility.remove')"
        :size="16"
        class="pe-5 iconStyle"
        @click="$emit('remove-user', user)">
        fas fa-trash
      </v-icon>
    </div>
  </div>
</template>

<script>

export default {
  props: {
    user: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      userVisibility: 'view',
    };
  },
  computed: {
    avatarUrl() {
      const profile = this.user && (this.user.profile || this.user.space);
      return profile && (profile.avatarUrl || profile.avatar);
    },
    displayName() {
      const profile = this.user && (this.user.profile || this.user.space);
      const fullName = profile && (profile.displayName || profile.fullname || profile.fullName);
      return fullName;
    },
  },
  methods: {
    visibilityUser(value){
      this.userVisibility=value;
    }
  }
};
</script>
