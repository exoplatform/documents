const path = require('path');
const ESLintPlugin = require('eslint-webpack-plugin');
const { VueLoaderPlugin } = require('vue-loader')

module.exports = {
  mode: 'production',
  context: path.resolve(__dirname, '.'),
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: [
          'babel-loader',
        ]
      },
      {
        test: /\.vue$/,
        use: [
          'vue-loader',
        ]
      }
    ]
  },
  plugins: [
    new ESLintPlugin({
      files: [
        './src/main/webapp/vue-app/*.js',
        './src/main/webapp/vue-app/*.vue',
        './src/main/webapp/vue-app/**/*.js',
        './src/main/webapp/vue-app/**/*.vue',
      ],
    }),
    new VueLoaderPlugin()
  ],
  entry: {
    documents: './src/main/webapp/vue-app/documents/main.js',
    documentsUserSettings: './src/main/webapp/vue-app/documents-user-setting/main.js',
    documentsSnackbarComponent : './src/main/webapp/vue-app/snackbar/main.js',
    documentsFavoriteDrawerExtensions: './src/main/webapp/vue-app/documents-favorite-drawer-extensions/main.js',
    documentsSizeGadget: './src/main/webapp/vue-app/documents-size-gadget/main.js',
    trashManagement: './src/main/webapp/vue-app/trash-management/main.js',
    downloadDocumentsPublicAccess: './src/main/webapp/vue-app/download-document/main.js',
    notificationExtension: './src/main/webapp/vue-app/notification-extension/main.js',
    documentsPreviewExtension: './src/main/webapp/vue-app/preview-extension/main.js',
    attachmentApp: './src/main/webapp/vue-app/attachment/main.js',
    attachmentIntegration: './src/main/webapp/vue-app/attachment-integration/main.js',
    documentsFolderPicker: './src/main/webapp/vue-app/documents-folder-picker/main.js',
    documentsFolderPickerApp: './src/main/webapp/vue-app/documents-folder-picker-app/main.js',
    documentsInfoDrawer: './src/main/webapp/vue-app/document-info-drawer/main.js',
    documentsOffline: './src/main/webapp/vue-app/documents-offline/main.js',
    documentsOfflineSettings: './src/main/webapp/vue-app/documents-offline-settings/main.js',
    documentsPwaExtension: './src/main/webapp/vue-app/documents-pwa-extension/main.js',
    documentGadget: './src/main/webapp/vue-app/document-gadget/main.js',
    filesSearch: './src/main/webapp/vue-app/files-search/main.js',
    restrictedDrive: './src/main/webapp/vue-app/restricted-drive/main.js',
  },
  output: {
    path: path.join(__dirname, 'target/documents-portlet/'),
    filename: 'js/[name].bundle.js',
    libraryTarget: 'amd'
  },
  externals: {
    vue: 'Vue',
    vuetify: 'Vuetify',
    jquery: '$',
  },
};
