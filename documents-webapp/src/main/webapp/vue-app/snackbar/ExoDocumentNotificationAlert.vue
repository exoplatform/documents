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
      <div>
        <span class="text-color">
          {{ alertMessage }}
        </span>
        <div class="text-color">
          {{ administratorMessage }}
        </div>
      </div>
      <v-btn
        v-if="alert.click"
        class="primary--text pl-3"
        text
        @click="alert.click">
        {{ alert.clickMessage }}
      </v-btn>
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