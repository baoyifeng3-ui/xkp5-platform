const assert = require('assert')
const { requireMatchingPaper } = require('../src/utils/t100Paper')

const matched = requireMatchingPaper({
  code: 1,
  data: { paperType: 'b', name: 'b/dataset/test/1.jpg' }
}, 'B')
assert.strictEqual(matched.name, 'b/dataset/test/1.jpg')

assert.throws(
  () => requireMatchingPaper({ code: 1, data: {} }, 'B'),
  /未支持按赛卷加载模型/
)

assert.throws(
  () => requireMatchingPaper({ code: 1, data: { paperType: 'A' } }, 'B'),
  /返回了 A 卷结果，当前需要 B 卷/
)

assert.throws(
  () => requireMatchingPaper({ code: 1002, message: 'B 卷模型未部署' }, 'B'),
  /B 卷模型未部署/
)

assert.throws(
  () => requireMatchingPaper({ code: 1002, message: '模型尚未部署或部署失败' }, 'A'),
  /T100 服务仍在运行旧版本或未部署模型/
)

console.log('T100 paper contract tests passed')
