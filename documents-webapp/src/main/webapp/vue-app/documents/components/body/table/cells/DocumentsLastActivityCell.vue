<template>
  <div class="documents-last-activity">
    <template v-if="activityLabel">
      {{ activityLabel }}
    </template>
    <template v-else>
      -
    </template>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
  },
  computed: {
    activityLabel() {
      if (this.file && this.file.auditTrails && this.file.auditTrails.trails && this.file.auditTrails.trails.length) {
        const lastActivity = this.file.auditTrails.trails[0];
        if (lastActivity && lastActivity.userIdentity && lastActivity.userIdentity.profile && lastActivity.userIdentity.profile.fullname) {
          return this.$t(`documents.label.activity.${lastActivity.actionType}`, {0: lastActivity.userIdentity.profile.fullname});
        }
      }
      return null;
    },
  },
};
</script>