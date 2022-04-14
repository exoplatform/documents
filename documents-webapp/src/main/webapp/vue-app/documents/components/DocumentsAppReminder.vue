<template>
  <v-main>
    <changes-reminder :reminder="introduceNewDocAppReminder" />
    <changes-reminder :reminder="switchOldDocAppReminder" />
  </v-main>
</template>

<script>
export default {
  data () {
    return {
      introduceNewDocAppReminder: {
        name: 'IntroduceNewDocApp',
        title: this.$t('documents.reminder.newDocApp.dialog.title'),
        img: '/documents-portlet/images/newdocapp.png',
        description: this.$t('documents.reminder.newDocApp.Dialog.message'),
      },
      switchOldDocAppReminder: {
        name: 'SwitchOldDocuments',
        title: this.$t('documents.reminder.oldDocSwitch.dialog.title'),
        img: '/documents-portlet/images/switcholdapp.gif',
        description: this.$t('documents.reminder.oldDocSwitch.Dialog.message'),
      },
    };
  },
  mounted() {
    window.setTimeout(() => {
      document.dispatchEvent(new CustomEvent('changes-reminder-open'));
    }, 800);
  }
};
</script>