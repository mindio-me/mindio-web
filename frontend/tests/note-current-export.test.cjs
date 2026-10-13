const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const { transformSync } = require('@babel/core')
const { parseComponent } = require('vue-template-compiler')

function loadComponent(file, dependencies = {}) {
  const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8')
  const { script } = parseComponent(source)
  const { code } = transformSync(script.content, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  })
  const module = { exports: {} }
  vm.runInNewContext(code, {
    module, exports: module.exports,
    require: name => dependencies[name] || {},
    setTimeout, clearTimeout, console
  })
  return module.exports.default
}

function exportContext(overrides = {}) {
  const downloads = [], requests = [], errors = []
  const component = loadComponent('pages/workspace/notes/index.vue', {
    '~/utils/note-backup': {
      isDesktopRuntime: () => false,
      downloadBlob: (blob, name) => downloads.push({ blob, name }),
      errorMessageOf: async e => e.message
    }
  })
  const context = {
    activeNote: { id: 7, title: 'A:/B', contentType: 'editorjs' },
    exportingCurrent: false, isSaving: false, hasUnsavedChanges: true,
    $t: key => key,
    $message: { success() {}, error: message => errors.push(message) },
    $noteService: { exportNotes: async request => {
      requests.push(request)
      return { blob: 'package', noteCount: 1, missingAssets: 0 }
    } },
    async flushPendingSave() { this.hasUnsavedChanges = false },
    ...overrides
  }
  return { context, downloads, requests, errors, methods: component.methods }
}

test('single export saves first and downloads only the selected note', async () => {
  const { context, downloads, requests, errors, methods } = exportContext()
  await methods.exportCurrentNote.call(context, context.activeNote)
  assert.equal(requests.length, 1)
  assert.deepEqual(Array.from(requests[0].noteIds), [7])
  assert.equal(requests[0].exportedFrom, 'web')
  assert.deepEqual(downloads, [{ blob: 'package', name: 'A__B.mindio' }])
  assert.deepEqual(errors, [])
  assert.equal(context.exportingCurrent, false)
})

test('failed or still-running saves block export', async () => {
  for (const overrides of [
    { flushPendingSave: async () => {} },
    { isSaving: true, hasUnsavedChanges: false }
  ]) {
    const { context, requests, errors, methods } = exportContext(overrides)
    await methods.exportCurrentNote.call(context, context.activeNote)
    assert.equal(requests.length, 0)
    assert.equal(errors.length, 1)
    assert.equal(context.exportingCurrent, false)
  }
})

test('forced save captures changes before delayed editor notifications', async () => {
  const { methods } = exportContext()
  const context = {
    hasUnsavedChanges: false, isEditorjsNote: true, editor: {},
    async waitForSaveIdle() {},
    async saveToBackend() { assert.equal(this.hasUnsavedChanges, true); this.hasUnsavedChanges = false }
  }
  await methods.flushPendingSave.call(context, { force: true })
  assert.equal(context.hasUnsavedChanges, false)
  context.saveToBackend = async () => {}
  await methods.flushPendingSave.call(context, { force: true })
  assert.equal(context.hasUnsavedChanges, true)
})

test('failed saves also prevent import submission', async () => {
  const component = loadComponent('components/NoteImportDialog.vue', {
    '~/utils/note-backup': { errorMessageOf: async e => e.message }
  })
  let committed = false
  const context = {
    committing: false, preview: { importToken: 'token' }, choices: {},
    async beforeCommit() { throw Error('Save failed') },
    $noteService: { async commitImport() { committed = true } },
    $t: key => key, $message: { error() {} }, close() {}
  }
  await component.methods.commit.call(context)
  assert.equal(committed, false)
  assert.equal(context.committing, false)
})

test('switching notes during the save blocks export', async () => {
  const { context, requests, errors, methods } = exportContext({
    async flushPendingSave() { this.hasUnsavedChanges = false; this.activeNote = { id: 8 } }
  })
  await methods.exportCurrentNote.call(context, context.activeNote)
  assert.equal(requests.length, 0)
  assert.match(errors[0], /noteChangedBeforeExport/)
})

test('repeated clicks and non-editorjs notes do not export', async () => {
  const { context, requests, methods } = exportContext({ exportingCurrent: true })
  await methods.exportCurrentNote.call(context, context.activeNote)
  context.exportingCurrent = false
  await methods.exportCurrentNote.call(context, { id: 9, contentType: 'markdown' })
  assert.equal(requests.length, 0)
})

test('Windows reserved filenames are escaped', async () => {
  const { context, downloads, methods } = exportContext()
  context.activeNote.title = 'CON'
  await methods.exportCurrentNote.call(context, context.activeNote)
  assert.equal(downloads[0].name, '_CON.mindio')
})

test('export errors restore the menu state', async () => {
  const { context, errors, methods } = exportContext({
    $noteService: { async exportNotes() { throw Error('network failed') } }
  })
  await methods.exportCurrentNote.call(context, context.activeNote)
  assert.match(errors[0], /network failed/)
  assert.equal(context.exportingCurrent, false)
})

test('importing an overwrite reloads the current editor before refreshing the list', async () => {
  const { methods } = exportContext()
  const calls = []
  const context = {
    activeNote: { id: 7 }, activeNoteId: 7,
    $noteService: { async findIdByUid(uid) { assert.equal(uid, 'original-uuid'); return { id: 7 } } },
    async reloadFromServer() { calls.push('reload') },
    async loadNotes() { calls.push('list') }
  }
  await methods.handleMindioImported.call(context, { overwritten: 1 }, ['original-uuid'])
  assert.deepEqual(calls, ['reload', 'list'])
  calls.length = 0
  await methods.handleMindioImported.call(context, { overwritten: 0 })
  assert.deepEqual(calls, ['list'])
})

test('import awaits save and refresh callbacks before closing', async () => {
  const component = loadComponent('components/NoteImportDialog.vue')
  const calls = []
  const context = {
    committing: false, preview: { importToken: 'token' }, choices: { uid: 'overwrite' },
    async beforeCommit() { calls.push('save') },
    async afterCommit() { calls.push('refresh') },
    $noteService: { async commitImport(token, overwrite) {
      assert.equal(token, 'token'); assert.deepEqual(Array.from(overwrite), ['uid'])
      calls.push('commit'); return { created: 0, overwritten: 1, kept: 0 }
    } },
    $t: key => key, $message: { success() {}, error() { assert.fail('Unexpected import error') } },
    $emit() { calls.push('emit') }, close() { calls.push('close') }
  }
  await component.methods.commit.call(context)
  assert.deepEqual(calls, ['save', 'commit', 'refresh', 'emit', 'close'])
  assert.equal(context.committing, false)
})
