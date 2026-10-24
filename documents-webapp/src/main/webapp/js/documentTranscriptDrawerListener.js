function() {
    document.addEventListener('documents-open-transcript', event => {
      if (event?.detail?.documentId) {
        event.preventDefault();
        window.require(["SHARED/DocumentsTranscriptDrawer"], transcriptDrawer => {
          transcriptDrawer.init(event.detail);
        });
      }
    });
}();
