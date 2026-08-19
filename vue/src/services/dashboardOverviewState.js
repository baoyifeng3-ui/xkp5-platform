function applyOverviewSuccess (state, snapshot) {
  state.snapshot = snapshot
  state.stale = snapshot && snapshot.fresh === false
  state.firstLoadFailed = false
}

function applyOverviewFailure (state) {
  if (state.snapshot) state.stale = true
  else state.firstLoadFailed = true
}

function resourceText (summary) {
  if (!summary || summary.sampleCount === 0 || summary.averagePercent == null) return '暂无数据'
  return `${Number(summary.averagePercent).toFixed(1)}%`
}

module.exports = { applyOverviewSuccess, applyOverviewFailure, resourceText }
