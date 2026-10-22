// Only presentation state is changed here; form values and validation stay in the page.
function updateAndScroll(page, api, patch, scroll) {
  const version = page._formFeedbackVersion = (page._formFeedbackVersion || 0) + 1
  page.setData(patch, () => {
    // setData's callback runs after rendering, including a previously hidden step.
    if (version !== page._formFeedbackVersion) return
    if (typeof api.pageScrollTo === 'function') api.pageScrollTo(scroll)
  })
}

function showError(page, api, error, patch = {}) {
  updateAndScroll(page, api, { ...patch, formError: error }, { selector: '#' + error.target, duration: 180 })
  api.showToast({ title: error.message, icon: 'none' })
}

function changeStep(page, api, patch) {
  updateAndScroll(page, api, { ...patch, formError: null }, { scrollTop: 0, duration: 0 })
}

function clearError(page) {
  if (!page.data.formError) return
  cancel(page)
  page.setData({ formError: null })
}

function cancel(page) { page._formFeedbackVersion = (page._formFeedbackVersion || 0) + 1 }

module.exports = { showError, changeStep, clearError, cancel }
