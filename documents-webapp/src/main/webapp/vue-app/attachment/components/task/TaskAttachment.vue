<template>
  <div class="taskAttachments d-flex">
    <attachment-app
      :entity-id="taskId"
      :space-id="spaceId"
      entity-type="task">
      <template #attachmentsList></template>
      <template #attachedFilesList="{attachments}">
        <div class="attachmentsPreview v-card__text ms-3 pa-0">
          <a
            v-if="attachments.length"
            class="viewAllAttachments primary--text font-weight-bold text-decoration-underline">
            {{ $t('attachments.view.all') }} ({{ attachments.length }})
          </a>
        </div>
      </template>
    </attachment-app>
  </div>
</template>
<script>
export default {
  props: {
    taskId: {
      type: String,
      default: null,
    },
    spaceId: {
      type: String,
      default: null,
    },
  },
};
</script>