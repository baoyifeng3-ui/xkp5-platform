import { openAsBlob } from 'node:fs'
import { readdir, stat } from 'node:fs/promises'
import { basename, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const normalizeCategory = value => value.replace(/^\d+类\s*/, '').trim()
export const normalizeCourse = value => value.replace(/^\d+\s*/, '').trim()
export const normalizeChapter = value => value.replace(/\s+/g, ' ').trim()
export const naturalCompare = (left, right) => left.localeCompare(right, 'zh-CN', { numeric: true, sensitivity: 'base' })

export async function scanLibrary (root) {
  const courses = []
  for (const categoryEntry of (await readdir(root, { withFileTypes: true })).filter(item => item.isDirectory()).sort((a, b) => naturalCompare(a.name, b.name))) {
    const category = normalizeCategory(categoryEntry.name)
    const categoryPath = join(root, categoryEntry.name)
    for (const courseEntry of (await readdir(categoryPath, { withFileTypes: true })).filter(item => item.isDirectory()).sort((a, b) => naturalCompare(a.name, b.name))) {
      const coursePath = join(categoryPath, courseEntry.name)
      const files = (await readdir(coursePath, { withFileTypes: true }))
        .filter(item => item.isFile() && extname(item.name).toLowerCase() === '.pdf')
        .sort((a, b) => naturalCompare(a.name, b.name))
      const chapters = []
      for (let index = 0; index < files.length; index++) {
        const path = join(coursePath, files[index].name)
        chapters.push({ name: basename(files[index].name, extname(files[index].name)), fileName: files[index].name, path, size: (await stat(path)).size, sortOrder: index })
      }
      courses.push({ category, name: normalizeCourse(courseEntry.name), path: coursePath, chapters })
    }
  }
  return courses
}

class Api {
  constructor (baseUrl) { this.baseUrl = baseUrl.replace(/\/$/, ''); this.token = '' }
  async request (path, options = {}) {
    const response = await fetch(this.baseUrl + path, { ...options, headers: { ...(options.headers || {}), ...(this.token ? { satoken: this.token } : {}) } })
    const result = await response.json()
    if (!response.ok || result.code !== 200) throw new Error(result.msg || `${response.status} ${path}`)
    return result.data
  }
  async login (username, password) {
    const body = new URLSearchParams({ userName: username, password })
    const data = await this.request('/user/login', { method: 'POST', body, headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
    this.token = data.tokenValue
  }
  get (path) { return this.request(path) }
  post (path, data) { return this.request(path, { method: 'POST', body: data == null ? undefined : JSON.stringify(data), headers: data == null ? {} : { 'Content-Type': 'application/json' } }) }
  put (path, data) { return this.request(path, { method: 'PUT', body: JSON.stringify(data), headers: { 'Content-Type': 'application/json' } }) }
  async upload (courseId, chapter) {
    const data = new FormData()
    data.append('file', await openAsBlob(chapter.path, { type: 'application/pdf' }), chapter.fileName)
    data.append('resourceType', 'EBOOK')
    data.append('courseIds', courseId)
    return this.request('/admin/resource-spaces/course/files', { method: 'POST', body: data })
  }
}

async function importCourse (api, source, existing) {
  let view = existing
  if (!view) view = await api.post('/admin/courses', { name: source.name, courseType: source.category, description: '', coverResourceId: '' })
  const course = view.course
  const chapters = await api.get(`/admin/courses/${course.courseId}/chapters`)
  const chapterByName = new Map(chapters.map(item => [normalizeChapter(item.chapterName), item]))
  const resources = new Map((view.resources || []).map(item => [item.name, item]))
  let uploaded = 0; let skipped = 0; const failures = []
  for (const item of source.chapters) {
    try {
      let chapter = chapterByName.get(normalizeChapter(item.name))
      if (!chapter) {
        chapter = await api.post(`/admin/courses/${course.courseId}/chapters`, { chapterName: item.name, practiceTool: null, sortOrder: item.sortOrder })
        chapterByName.set(item.name, chapter)
      }
      const resource = resources.get(item.fileName)
      if (resource && resource.chapterId === chapter.chapterId) { skipped++; continue }
      const uploadedFile = resource || await api.upload(course.courseId, item)
      await api.put(`/admin/courses/${course.courseId}/resources/${uploadedFile.fileId || uploadedFile.resourceId}/binding`, { chapterId: chapter.chapterId, practiceTool: null })
      uploaded++
    } catch (error) { failures.push({ file: item.path, error: error.message }) }
  }
  await api.post(`/admin/courses/${course.courseId}/enabled?enabled=true`)
  return { course: source.name, uploaded, skipped, failures }
}

function argument (name, fallback = '') {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}

export async function main () {
  const root = argument('--root')
  if (!root) throw new Error('缺少 --root 课程目录')
  const courses = await scanLibrary(resolve(root))
  const pdfs = courses.reduce((sum, item) => sum + item.chapters.length, 0)
  const bytes = courses.flatMap(item => item.chapters).reduce((sum, item) => sum + item.size, 0)
  console.log(`扫描完成：${new Set(courses.map(item => item.category)).size} 个类别，${courses.length} 门课程，${pdfs} 个 PDF，${(bytes / 1073741824).toFixed(2)} GiB`)
  if (!process.argv.includes('--execute')) return
  const api = new Api(argument('--base-url', 'http://localhost:19243'))
  await api.login(argument('--username', 'admin123'), argument('--password', 'admin123'))
  const existing = await api.get('/admin/courses')
  const byKey = new Map(existing.map(item => [`${item.course.courseType}\0${item.course.name}`, item]))
  const results = []
  for (let index = 0; index < courses.length; index++) {
    const source = courses[index]
    console.log(`[${index + 1}/${courses.length}] ${source.category} / ${source.name}`)
    results.push(await importCourse(api, source, byKey.get(`${source.category}\0${source.name}`)))
  }
  const uploaded = results.reduce((sum, item) => sum + item.uploaded, 0)
  const skipped = results.reduce((sum, item) => sum + item.skipped, 0)
  const failures = results.flatMap(item => item.failures.map(failure => ({ course: item.course, ...failure })))
  console.log(`导入完成：上传 ${uploaded}，跳过 ${skipped}，失败 ${failures.length}`)
  for (const failure of failures) console.error(`失败：${failure.course} / ${failure.file} / ${failure.error}`)
  if (failures.length) process.exitCode = 2
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main().catch(error => { console.error(error.message); process.exitCode = 1 })
