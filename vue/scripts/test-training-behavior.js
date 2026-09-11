const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
const compiler = require('vue-template-compiler')

function component(path, globals = {}) {
  const source = compiler.parseComponent(fs.readFileSync(path, 'utf8')).script.content
    .replace(/import[\s\S]*?from\s*["'][^"']+["'];?/g, '')
    .replace(/const \{ isPreviewRoute \} = require\([^;]+;/, '')
    .replace('export default', 'module.exports =')
  const context = { module: { exports: {} }, setTimeout, EnvironmentToolFrame: {}, ParticipantPreviewNotice: {}, CourseReportEditor: {}, DatasetUploadDialog: {}, ModelDeploymentDialog: {}, getUserName: () => '', ...globals }
  vm.runInNewContext(source, context)
  return context.module.exports
}

async function main() {
  let frameTimeout;
  const toolFrame = component('src/components/training/EnvironmentToolFrame.vue', {setTimeout: callback => {frameTimeout=callback;return 1}, clearTimeout:()=>{}});
  const frameContext = {...toolFrame.methods,unconfirmed:false,timer:null};
  frameContext.arm(); frameTimeout();
  assert.strictEqual(frameContext.unconfirmed,true,'unconfirmed iframe load must produce feedback');
  frameContext.loaded(); assert.strictEqual(frameContext.unconfirmed,false);
  let delayedPolls = 0;
  const delayed = component('src/views/user/TrainingEnvironment.vue', {
    setTimeout: fn => { fn(); return 1 },
    listUserTrainingEnvironments: async () => ({data:[{environmentId:'slow',actualState:++delayedPolls > 45 ? 'RUNNING' : 'STARTING'}]})
  });
  const delayedContext = {...delayed.methods, environments:[{environmentId:'slow'}], $set:(rows,i,row)=>{rows[i]=row}};
  assert.strictEqual((await delayedContext.waitForEnvironment('slow')).actualState,'RUNNING','slow startup must continue beyond the old 30-poll limit');
  let coursePolls = 0;
  const courseSlow = component('src/views/user/CoursePlatform.vue', { CourseLearningWorkspace: {}, CourseReportEditor: {}, defaultCourseCover: '', getClassPolicy:()=>({}) });
  const courseSlowContext = {...courseSlow.methods, courseEnvironments:[], async reloadEnvironments() { this.courseEnvironments=[{environmentId:'slow',actualState:++coursePolls>45?'RUNNING':'STARTING'}] }};
  // Use the existing VM helper's instant timer for a genuine >30 poll boundary.
  const courseFast = component('src/views/user/CoursePlatform.vue', { CourseLearningWorkspace:{}, CourseReportEditor:{}, defaultCourseCover:'', setTimeout:fn=>{fn();return 1}, getClassPolicy:()=>({}) });
  assert.strictEqual((await courseFast.methods.waitForWorkspaceEnvironment.call(courseSlowContext,'slow')).actualState,'RUNNING');
  const training = component('src/views/user/TrainingEnvironment.vue')
  const context = { ...training.data(), ...training.methods, embeddedUrl: 'https://old:9091', embeddedTitle: 'VS Code', reportVisible: true }
  if (training.watch && training.watch.adminDemoEnvironmentId) training.watch.adminDemoEnvironmentId.call(context)
  assert.strictEqual(context.embeddedUrl, '', 'switching environments must close the old tool')
  assert.strictEqual(context.reportVisible, false, 'switching environments must close the old report')
  const notices = []
  const upload = component('src/components/training/DatasetUploadDialog.vue', { uploadTrainingDataset: async () => { throw Error('offline') } })
  const uploadContext = { ...upload.data(), ...upload.methods, environment: { environmentId: 'test' }, queue: [{ file: { name: 'data.csv' } }], $message: { success: text => notices.push(['success', text]), error: text => notices.push(['error', text]), warning: text => notices.push(['warning', text]) } }
  await uploadContext.upload()
  assert.strictEqual(notices.some(([type]) => type === 'success'), false, 'failed uploads must not report success')
  assert.strictEqual(uploadContext.uploading, false)

  let submitted = 0
  const retry = component('src/components/training/DatasetUploadDialog.vue', { uploadTrainingDataset: async () => { submitted += 1 } })
  uploadContext.queue = [{ file: { name: 'ok.csv' }, status: 'success' }, { file: { name: 'retry.csv' }, status: 'exception' }]
  await retry.methods.upload.call(uploadContext)
  assert.strictEqual(submitted, 1, 'retry must submit only failed files')
  assert.strictEqual(uploadContext.queue[1].status, 'success')

  const preview = { ...training.methods, previewOnly: true, currentEnvironment: { actualState: 'RUNNING', environmentId: 'env', editorUrl: 'https://editor' }, openUrl: () => { throw Error('preview opened a real tool') } }
  await preview.openTool('VSCODE')
  const race = { ...training.methods, currentEnvironment: { actualState: 'STARTING', environmentId: 'old' }, $message: { info() {}, error() {} }, openUrl: () => { throw Error('opened stale environment') }, async waitForEnvironment() { this.currentEnvironment = { environmentId: 'new' }; return { environmentId: 'old', actualState: 'RUNNING', editorUrl: 'https://old' } } }
  await race.openTool('VSCODE')

  let polls = 0
  const failedTraining = component('src/views/user/TrainingEnvironment.vue', {
    setTimeout: fn => { fn(); return 1 },
    listUserTrainingEnvironments: async () => { polls += 1; return { data: [{ environmentId: 'env', actualState: 'DEGRADED' }] } }
  })
  const failedContext = { ...failedTraining.methods, adminDemo: false, environments: [{ environmentId: 'env' }], $set: (rows, index, value) => { rows[index] = value } }
  const failedEnvironment = await failedContext.waitForEnvironment('env')
  assert.strictEqual(failedEnvironment.actualState, 'DEGRADED', 'failed dependency must stop polling immediately')
  assert.strictEqual(polls, 1)

  const validationTemplate = compiler.parseComponent(fs.readFileSync('src/views/user/TrainingValidation.vue', 'utf8')).template.content
  const root = compiler.compile(validationTemplate).ast
  const back = root.children.find(node => node.tag === 'header').children.find(node => node.tag === 'el-button')
  let route
  vm.runInNewContext(back.events.click.value, { adminDemo: true, $router: { push: value => { route = value } } })
  assert.strictEqual(route, '/management/demo/training', 'admin validation must return to the admin environment page')

  let pendingSave
  const saves = []
  const report = component('src/components/course/CourseReportEditor.vue', { clearTimeout() {}, setTimeout: fn => { pendingSave = fn; return 1 }, saveCourseReport: async (course, content) => { saves.push([course, content]) } })
  const reportContext = { ...report.data(), ...report.methods, courseId: 'course-a', loadedFor: 'course-a', $refs: { editor: { innerHTML: 'report A' } } }
  reportContext.save = report.methods.save.bind(reportContext)
  reportContext.scheduleSave()
  reportContext.courseId = 'course-b'
  reportContext.$refs.editor.innerHTML = 'report B'
  await pendingSave()
  assert.deepStrictEqual(saves, [['course-a', 'report A']], 'delayed autosave must preserve the original course and content')
  console.log('training behavior tests passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
