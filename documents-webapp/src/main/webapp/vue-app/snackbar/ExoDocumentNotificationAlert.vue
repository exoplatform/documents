<template>
  <v-alert
    v-model="displayAlert"
    :type="alertType"
    border="left"
    class="white position-relative"
    elevation="2"
    dismissible
    colored-border
    outlined>
    <div class="d-flex">
      <div :class="isMobile ? 'pr-6' : 'pr-12'">
        <span class="text-color">
          {{ alertMessage }}
        </span>
        <div class="text-color">
          {{ administratorMessage }}
        </div>
      </div>
      <div :class="isMobile ? 'pt-0' : 'pt-3'">
        <v-btn
          v-if="alert.click"
          class="primary--text"
          text
          @click="alert.click">
          {{ alert.clickMessage }}
        </v-btn>
      </div>
      <v-btn
        slot="close"
        slot-scope="{toggle}"
        icon
        small
        light
        @click="toggle">
        <v-icon>close</v-icon>
      </v-btn>
    </div>
  </v-alert>
</template>

<script>
export default {
  props: {
    alert: {
      type: Object,
      default: null
    },
    isMobile: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    displayAlert: true,
  }),
  computed: {
    alertMessage() {
      return this.alert && this.alert.message;
    },
    administratorMessage() {
      return this.alert && this.alert.administratorMessage;
    },
    alertType() {
      return this.alert.type;
    },
  },
  watch: {
    displayAlert() {
      if (!this.displayAlert) {
        this.$emit('dismissed');
      }
    },
  },
  created() {
    const time = 5000;
    window.setTimeout(() => this.displayAlert = false, time);
  },
};
</script>