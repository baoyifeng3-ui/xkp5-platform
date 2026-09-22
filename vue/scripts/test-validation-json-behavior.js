const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const compiler = require('vue-template-compiler');

const source = compiler.parseComponent(fs.readFileSync('src/views/user/TrainingValidation.vue', 'utf8')).script.content
  .replace(/import[\s\S]*?from\s*["'][^"']+["'];?/g, '')
  .replace('export default', 'module.exports =');
let requestSeen = false;
const errors = [];
const scope = {
  module: { exports: {} },
  request: { post: async (url, body, config) => {
    assert.strictEqual(url, '/sample/httpToBase64');
    assert.strictEqual(config.headers['Content-Type'], 'application/json');
    assert.strictEqual(body.url, 'https://example.invalid/qa.jpg');
    requestSeen = true;
    return { data: { base64: 'cWE=' } };
  } },
};
vm.runInNewContext(source, scope);
const context = {
  adminDemo: true, selectedEnvironmentId: 'qa', mode: 'url', imageUrl: 'https://example.invalid/qa.jpg',
  environments: [{ environmentId: 'qa', actualState: 'RUNNING', t100Url: 'qa.invalid:5000' }],
  $store: { state: { Match: { activePaper: 'A' } } },
  $axios: { post: async () => ({ data: { code: 1, data: {} } }) },
  $message: { success() {}, error(message) { errors.push(message); } },
};
scope.module.exports.methods.validate.call(context).then(() => {
  assert.deepStrictEqual(errors, []);
  assert.ok(requestSeen);
  assert.strictEqual(context.loading, false);
  assert.strictEqual(context.result.code, 1);
  console.log('validation JSON behavior passed');
}).catch(error => { console.error(error); process.exitCode = 1; });
