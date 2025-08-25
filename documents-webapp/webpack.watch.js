const path = require('path');
const { merge } = require('webpack-merge');

const webpackProductionConfig = require('./webpack.prod.js');

module.exports = merge(webpackProductionConfig, {
  output: {
    path: 'D:\\\eXo\\servers\\platform-7.1.x-meeds-qaui-SNAPSHOT/webapps/documents-portlet/',
    filename: 'js/[name].bundle.js'
  },
  mode: 'development',
  devtool: 'eval-source-map'
});
