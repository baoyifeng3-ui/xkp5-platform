const fs = require('fs')
const path = require('path')
const files = [
  '../../java/match-mgr/src/main/java/com/match/environment/web/UserTrainingEnvironmentController.java',
  '../../java/match-mgr/src/main/java/com/match/environment/web/AdminTrainingEnvironmentController.java'
]
for (const file of files) {
  const source = fs.readFileSync(path.join(__dirname, file), 'utf8')
  if (!source.includes('https://') || !source.includes('getContainerPort()')) throw new Error(`${file}: editor URL is not HTTPS`)
}
console.log('editor HTTPS contract passed')
