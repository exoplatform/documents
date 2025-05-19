function() {
    document.addEventListener('open-document-info-drawer', event => {
      window.require(["SHARED/DocumentsInfoDrawer"], infoDrawer => {
        infoDrawer.init(event);
      });
    });
}();