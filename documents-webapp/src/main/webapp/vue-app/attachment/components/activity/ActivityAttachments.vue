<template>
  <card-carousel parent-class="activity-files-parent">
    <activity-attachment
      v-for="(attachment, index) in attachments"
      :key="attachment.id"
      :activity="activity"
      :index="index"
      :count="attachmentsCount"
      :attachment="attachment"
      :attachments="attachments"
      :preview-width="previewWidth"
      :preview-height="previewHeight"
      class="activity-file-item" />
  </card-carousel>
</template>

<script>
export default {
  props: {
    activity: {
      type: Object,
      default: null,
    },
    previewHeight: {
      type: String,
      default: () => '152px',
    },
    previewWidth: {
      type: String,
      default: () => '250px',
    },
  },
  computed: {
    attachments() {
      if (!this.activity.files) {
        return [];
      }
      const attachments = [];
      this.activity.files.forEach(attachment => {
        const mimetype = attachment.mimeType;
        let name = attachment.name;
        try {
          name = decodeURIComponent(name.replace(/%25/g, '%').replace(/%([^2][^5])/g, '%25$1'));
        } catch (e) {
          // could happen, but ignore it
        }
        const imageURL = this.getImageUrl(attachment);
        attachments.push({
          id: attachment.id,
          image: imageURL,
          downloadUrl: this.getDownloadUrl(attachment),
          name,
          filename: name,
          mimetype,
          icon: this.getFileIcon(attachment),
          editable: this.isFileEditable(attachment),
          readable: this.isFileReadable(attachment),
          path: attachment.docPath,
          source: 'documents'
        });
      });
      return attachments;
    },
    attachmentsCount() {
      return this.attachments.length;
    },
  },
  methods: {
    getFileIcon(file) {
      return this.$documentsIconsExtension?.[0]?.get?.(file?.mimeType);
    },
    isFileEditable(file) {
      return  this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === file.mimeType ).length > 0;
    },
    isFileReadable(file) {
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.mimeType === file.mimeType).length > 0;
    },
    getImageUrl(file) {
      file.readable = this.isFileReadable(file);
      return this.$documentsUtils.getThumbnailUrl(file,'250x250',file.lastModified);
    },
    getDownloadUrl(file) {
      return this.$documentsUtils.getDownloadUrl(file.id,file.lastModified);
    },
  }
};
</script>