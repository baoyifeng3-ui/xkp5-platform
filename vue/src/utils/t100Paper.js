function normalizePaperType (paperType) {
  const paper = String(paperType || '').trim().toUpperCase()
  return /^[A-Z]$/.test(paper) ? paper : ''
}

function requireMatchingPaper (payload, expectedPaperType) {
  const expectedPaper = normalizePaperType(expectedPaperType)
  if (!expectedPaper) throw new Error('当前赛卷无效')
  if (!payload || Number(payload.code) !== 1) {
    const message = String((payload && payload.message) || '')
    if (message === '模型尚未部署或部署失败') {
      throw new Error('T100 服务仍在运行旧版本或未部署模型，请在训练服务器启动新的 python/app.py 后重试')
    }
    throw new Error(message || '推理服务请求失败')
  }

  const actualPaper = normalizePaperType(payload.data && payload.data.paperType)
  if (!actualPaper) throw new Error('推理服务未支持按赛卷加载模型')
  if (actualPaper !== expectedPaper) {
    throw new Error(`推理服务返回了 ${actualPaper} 卷结果，当前需要 ${expectedPaper} 卷`)
  }
  return payload.data
}

module.exports = { normalizePaperType, requireMatchingPaper }
