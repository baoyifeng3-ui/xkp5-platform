'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const name = 'dist' // page title

// const port = process.env.port || process.env.npm_config_port || 9527 // dev port
const port = process.env.MATCH_FRONTEND_PORT || process.env.port || process.env.npm_config_port || 19140
const backendTarget = process.env.MATCH_BACKEND_URL || 'http://localhost:19141'
const fastdfsTarget = process.env.MATCH_FASTDFS_HTTP || 'http://localhost:8888'

module.exports = {
  publicPath: '/',
  outputDir: 'dist',
  assetsDir: 'static',
  // lintOnSave: process.env.NODE_ENV === 'development',
  // 关闭ESLint规范
  lintOnSave: false,
  // 打包后编辑指令  true/false
  productionSourceMap: false,
  devServer: {
    port: port,
    open: false,
    overlay: {
      warnings: false,
      errors: true
    },
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
        onProxyReq(proxyReq) {
          // The dev server is same-origin to the browser; do not forward its
          // development Origin to a backend with a production allowlist.
          proxyReq.removeHeader('origin')
        },
        pathRewrite: { '^/api': '' }
      },
      '/files': {
        target: fastdfsTarget,
        changeOrigin: true,
        pathRewrite: { '^/files': '' }
      }
    },
    before(app) {
      const express = require('express')
      app.use('/dataset', express.static(path.resolve(__dirname, '../download/dataset')))
    }
  },
  configureWebpack: {
    name: name,
    resolve: {
      extensions: ['.js', '.vue', '.json'],
      alias: {
        'vue$': 'vue/dist/vue.esm.js',
        '@': resolve('src'),
        'scss_vars': '@/styles/vars.scss',
        'excel': path.resolve(__dirname, '../src/excel'),//新增一行
      }
    }
  },
  css: {
    loaderOptions: {
      css: {
        // 这里的选项会传递给 css-loader
        importLoaders: 1,
      },
      less: {
        // 这里的选项会传递给 postcss-loader
        importLoaders: 1,
      }
    }
  },
  // 全局使用less变量
  pluginOptions: {
    'style-resources-loader': {
      preProcessor: 'less',
      patterns: [
        path.resolve(__dirname, './src/theme/variable.less')
      ]
    }
  },
  chainWebpack(config) {
    config.plugins.delete('preload') // TODO: need dist
    config.plugins.delete('prefetch') // TODO: need dist

    // 设置svg精灵加载程序
    config.module
      .rule('svg')
      .exclude.add(resolve('src/icons'))
      .end()
    config.module
      .rule('icons')
      .test(/\.svg$/)
      .include.add(resolve('src/icons'))
      .end()
      .use('svg-sprite-loader')
      .loader('svg-sprite-loader')
      .options({
        symbolId: 'icon-[name]'
      })
      .end()

    config.module
      .rule('vue')
      .use('vue-loader')
      .loader('vue-loader')
      .tap(options => {
        options.compilerOptions.preserveWhitespace = true
        return options
      })
      .end()

    config
      .when(process.env.NODE_ENV === 'development',
        config => config.devtool('cheap-source-map')
      )

  }
}
