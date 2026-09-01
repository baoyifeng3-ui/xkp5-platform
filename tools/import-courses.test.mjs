import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { naturalCompare, normalizeCategory, normalizeChapter, normalizeCourse, scanLibrary } from './import-courses.mjs'

test('normalizes numbered category and course names', () => {
  assert.equal(normalizeCategory('01类 数学与统计'), '数学与统计')
  assert.equal(normalizeCourse('03 线性代数与解析几何'), '线性代数与解析几何')
  assert.equal(normalizeChapter('第五章  作业提交 '), '第五章 作业提交')
})

test('sorts chapter numbers naturally', () => {
  assert.deepEqual(['第10章.pdf', '第2章.pdf', '第1章.pdf'].sort(naturalCompare),
    ['第1章.pdf', '第2章.pdf', '第10章.pdf'])
})

test('scans only pdf files as course chapters', async () => {
  const root = await mkdtemp(join(tmpdir(), 'xkp-course-import-'))
  const course = join(root, '01类 数学与统计', '01 工科数学分析')
  await mkdir(course, { recursive: true })
  await writeFile(join(course, '第10章.pdf'), 'ten')
  await writeFile(join(course, '第2章.pdf'), 'two')
  await writeFile(join(course, '说明.txt'), 'skip')

  const result = await scanLibrary(root)

  assert.equal(result.length, 1)
  assert.equal(result[0].category, '数学与统计')
  assert.equal(result[0].name, '工科数学分析')
  assert.deepEqual(result[0].chapters.map(item => item.name), ['第2章', '第10章'])
})
