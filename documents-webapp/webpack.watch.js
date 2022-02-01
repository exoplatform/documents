const path = require('path');
const merge = require('webpack-merge');

const webpackProductionConfig = require('./webpack.prod.js');

module.exports = merge(webpackProductionConfig, {
  output: {
    path: 'D:\\\eXo\\servers\\platform-6.3.0-M19\\webapps\\documents-portlet\\',
    filename: 'js/[name].bundle.js'
  }
});
