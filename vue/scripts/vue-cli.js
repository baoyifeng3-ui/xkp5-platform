const { spawnSync } = require('child_process')

const majorVersion = Number(process.versions.node.split('.')[0])
const env = Object.assign({}, process.env)

if (majorVersion >= 17 && !(env.NODE_OPTIONS || '').includes('--openssl-legacy-provider')) {
  env.NODE_OPTIONS = `${env.NODE_OPTIONS || ''} --openssl-legacy-provider`.trim()
}

const cli = require.resolve('@vue/cli-service/bin/vue-cli-service.js')
const result = spawnSync(process.execPath, [cli].concat(process.argv.slice(2)), {
  env,
  stdio: 'inherit'
})

process.exit(result.status === null ? 1 : result.status)
