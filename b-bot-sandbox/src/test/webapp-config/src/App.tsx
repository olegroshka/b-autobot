import { useState, useEffect } from 'react'
import type { ConfigValue } from './types'
import { fetchNamespaces, fetchTypes, fetchConfig, saveConfig, deleteConfig } from './api'

// ── Root ──────────────────────────────────────────────────────────────────────

export default function App() {
  const [namespaces,    setNamespaces]    = useState<string[]>([])
  const [selectedNs,   setSelectedNs]    = useState<string | null>(null)
  const [types,        setTypes]         = useState<string[]>([])
  const [selectedType, setSelectedType]  = useState<string>('')
  const [config,       setConfig]        = useState<Record<string, Record<string, ConfigValue>>>({})
  const [saveStatus,   setSaveStatus]    = useState<Record<string, string>>({})
  const [error,        setError]         = useState<string | null>(null)

  // Add / Clone dialog state
  const [adding,       setAdding]        = useState(false)
  const [cloningKey,   setCloningKey]    = useState<string | null>(null)
  const [newKeyInput,  setNewKeyInput]   = useState('')

  useEffect(() => {
    fetchNamespaces()
      .then(setNamespaces)
      .catch(e => setError(String(e)))
  }, [])

  useEffect(() => {
    if (!selectedNs) return
    setTypes([])
    setSelectedType('')
    setConfig({})
    fetchTypes(selectedNs)
      .then(t => { setTypes(t); if (t.length > 0) setSelectedType(t[0]) })
      .catch(e => setError(String(e)))
  }, [selectedNs])

  useEffect(() => {
    if (!selectedNs || !selectedType) return
    setConfig({})
    setAdding(false)
    setCloningKey(null)
    fetchConfig(selectedNs, selectedType)
      .then(setConfig)
      .catch(e => setError(String(e)))
  }, [selectedNs, selectedType])

  // ── Schema helper — derive default field values from existing entries ─────

  const schemaDefaults = (): Record<string, ConfigValue> => {
    const first = Object.values(config)[0]
    if (!first) return { value: '' }
    return Object.fromEntries(
      Object.entries(first).map(([k, v]) => [k,
        typeof v === 'boolean' ? false :
        typeof v === 'number'  ? 0     : ''
      ])
    )
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  const handleSave = async (key: string, updated: Record<string, ConfigValue>) => {
    if (!selectedNs || !selectedType) return
    setSaveStatus(s => ({ ...s, [key]: 'saving…' }))
    try {
      await saveConfig(selectedNs, selectedType, key, updated)
      setConfig(c => ({ ...c, [key]: updated }))
      setSaveStatus(s => ({ ...s, [key]: 'saved ✓' }))
    } catch (e) {
      setSaveStatus(s => ({ ...s, [key]: 'error ✗' }))
      setError(String(e))
    }
  }

  // ── Add ───────────────────────────────────────────────────────────────────

  const handleAdd = async (keyName: string, fields: Record<string, ConfigValue>) => {
    if (!selectedNs || !selectedType || !keyName.trim()) return
    const key = keyName.trim()
    try {
      await saveConfig(selectedNs, selectedType, key, fields)
      setConfig(c => ({ ...c, [key]: fields }))
      setAdding(false)
      setNewKeyInput('')
    } catch (e) { setError(String(e)) }
  }

  // ── Clone ─────────────────────────────────────────────────────────────────

  const handleClone = async (sourceKey: string, newKey: string) => {
    if (!selectedNs || !selectedType || !newKey.trim()) return
    const key = newKey.trim()
    const fields = { ...config[sourceKey] }
    try {
      await saveConfig(selectedNs, selectedType, key, fields)
      setConfig(c => ({ ...c, [key]: fields }))
      setCloningKey(null)
      setNewKeyInput('')
    } catch (e) { setError(String(e)) }
  }

  // ── Remove ────────────────────────────────────────────────────────────────

  const handleRemove = async (key: string) => {
    if (!selectedNs || !selectedType) return
    if (!window.confirm(`Remove "${key}" from ${selectedNs}/${selectedType}?`)) return
    try {
      await deleteConfig(selectedNs, selectedType, key)
      setConfig(c => { const copy = { ...c }; delete copy[key]; return copy })
      setSaveStatus(s => { const copy = { ...s }; delete copy[key]; return copy })
    } catch (e) { setError(String(e)) }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="cs-root">
      <header className="cs-header">
        <span className="cs-title">Config Service</span>
        <span className="cs-subtitle">In-memory configuration store</span>
      </header>

      {error && (
        <div className="cs-error">
          {error}
          <button onClick={() => setError(null)}>✕</button>
        </div>
      )}

      <div className="cs-layout">
        {/* ── Namespace list ─────────────────────────────────────────────── */}
        <nav className="cs-namespaces">
          <div className="cs-panel-label">Namespaces</div>
          {namespaces.map(ns => (
            <div
              key={ns}
              className={`cs-ns-item${selectedNs === ns ? ' selected' : ''}`}
              onClick={() => setSelectedNs(ns)}
              role="button"
              tabIndex={0}
              onKeyDown={e => e.key === 'Enter' && setSelectedNs(ns)}
            >
              {ns}
            </div>
          ))}
        </nav>

        {/* ── Right panel ────────────────────────────────────────────────── */}
        <main className="cs-main">
          {selectedNs && (
            <>
              {/* Type row + Add button */}
              <div className="cs-type-row">
                <label htmlFor="type-select">Type</label>
                <select
                  id="type-select"
                  value={selectedType}
                  onChange={e => setSelectedType(e.target.value)}
                >
                  {types.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
                <button
                  className="cs-add-btn"
                  onClick={() => { setAdding(true); setCloningKey(null); setNewKeyInput('') }}
                  title="Add new entry"
                >＋ Add</button>
              </div>

              {/* Existing entries */}
              {Object.entries(config).map(([key, fields]) => (
                <div key={key}>
                  <ConfigRow
                    entryKey={key}
                    fields={fields}
                    status={saveStatus[key] ?? ''}
                    onSave={updated => handleSave(key, updated)}
                    onClone={() => { setCloningKey(key); setAdding(false); setNewKeyInput('') }}
                    onRemove={() => handleRemove(key)}
                  />
                  {/* Inline Clone form */}
                  {cloningKey === key && (
                    <InlineKeyForm
                      label={`Clone "${key}" as…`}
                      value={newKeyInput}
                      onChange={setNewKeyInput}
                      onConfirm={() => handleClone(key, newKeyInput)}
                      onCancel={() => setCloningKey(null)}
                      confirmLabel="Clone"
                    />
                  )}
                </div>
              ))}

              {/* Add form */}
              {adding && (
                <AddForm
                  defaults={schemaDefaults()}
                  keyInput={newKeyInput}
                  onKeyChange={setNewKeyInput}
                  onConfirm={fields => handleAdd(newKeyInput, fields)}
                  onCancel={() => setAdding(false)}
                />
              )}
            </>
          )}
        </main>
      </div>
    </div>
  )
}

// ── ConfigRow ─────────────────────────────────────────────────────────────────

interface ConfigRowProps {
  entryKey: string
  fields:   Record<string, ConfigValue>
  status:   string
  onSave:   (updated: Record<string, ConfigValue>) => void
  onClone:  () => void
  onRemove: () => void
}

function ConfigRow({ entryKey, fields, status, onSave, onClone, onRemove }: ConfigRowProps) {
  const [local, setLocal] = useState<Record<string, ConfigValue>>(fields)
  useEffect(() => { setLocal(fields) }, [fields])

  const setField = (field: string, raw: string) => {
    const existing = fields[field]
    let value: ConfigValue
    if (typeof existing === 'boolean')       value = raw === 'true'
    else if (typeof existing === 'number')   value = parseFloat(raw) || 0
    else                                     value = raw
    setLocal(l => ({ ...l, [field]: value }))
  }

  return (
    <div className="cs-entry">
      <div className="cs-entry-header">
        <span className="cs-entry-key">{entryKey}</span>
        <div className="cs-entry-btns">
          <button className="cs-row-btn clone-btn" onClick={onClone} title="Clone this entry">Clone</button>
          <button className="cs-row-btn remove-btn" onClick={onRemove} title="Delete this entry">Remove</button>
        </div>
      </div>
      <div className="cs-entry-fields">
        {Object.entries(local).map(([field, value]) => (
          <div key={field} className="cs-field-row">
            <span className="cs-field-name">{field}</span>
            {typeof value === 'boolean' ? (
              <select value={String(value)} onChange={e => setField(field, e.target.value)} aria-label={`${entryKey}.${field}`}>
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : (
              <input type="text" value={String(value)} onChange={e => setField(field, e.target.value)} aria-label={`${entryKey}.${field}`} />
            )}
          </div>
        ))}
      </div>
      <div className="cs-entry-actions">
        <button className="cs-save-btn" onClick={() => onSave(local)}>SAVE</button>
        {status && <span className="cs-status">{status}</span>}
      </div>
    </div>
  )
}

// ── InlineKeyForm (used for Clone) ────────────────────────────────────────────

interface InlineKeyFormProps {
  label:        string
  value:        string
  onChange:     (v: string) => void
  onConfirm:    () => void
  onCancel:     () => void
  confirmLabel: string
}

function InlineKeyForm({ label, value, onChange, onConfirm, onCancel, confirmLabel }: InlineKeyFormProps) {
  return (
    <div className="cs-inline-form">
      <span className="cs-inline-label">{label}</span>
      <input
        className="cs-key-input"
        type="text"
        placeholder="new-key-name"
        value={value}
        onChange={e => onChange(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter') onConfirm(); if (e.key === 'Escape') onCancel() }}
        autoFocus
      />
      <button className="cs-save-btn" onClick={onConfirm}>{confirmLabel}</button>
      <button className="cs-cancel-btn" onClick={onCancel}>Cancel</button>
    </div>
  )
}

// ── AddForm ───────────────────────────────────────────────────────────────────

interface AddFormProps {
  defaults:    Record<string, ConfigValue>
  keyInput:    string
  onKeyChange: (v: string) => void
  onConfirm:   (fields: Record<string, ConfigValue>) => void
  onCancel:    () => void
}

function AddForm({ defaults, keyInput, onKeyChange, onConfirm, onCancel }: AddFormProps) {
  const [fields, setFields] = useState<Record<string, ConfigValue>>(defaults)
  useEffect(() => { setFields(defaults) }, [defaults])

  const setField = (field: string, raw: string) => {
    const existing = defaults[field]
    let value: ConfigValue
    if (typeof existing === 'boolean')     value = raw === 'true'
    else if (typeof existing === 'number') value = parseFloat(raw) || 0
    else                                   value = raw
    setFields(f => ({ ...f, [field]: value }))
  }

  return (
    <div className="cs-entry cs-add-form">
      <div className="cs-entry-header">
        <span className="cs-entry-key" style={{ color: '#4caf80' }}>＋ New entry</span>
      </div>
      <div className="cs-field-row" style={{ marginBottom: 8 }}>
        <span className="cs-field-name" style={{ fontWeight: 700 }}>Key (login)</span>
        <input
          className="cs-key-input"
          type="text"
          placeholder="e.g. jonesd"
          value={keyInput}
          onChange={e => onKeyChange(e.target.value)}
          onKeyDown={e => e.key === 'Escape' && onCancel()}
          autoFocus
        />
      </div>
      <div className="cs-entry-fields">
        {Object.entries(fields).map(([field, value]) => (
          <div key={field} className="cs-field-row">
            <span className="cs-field-name">{field}</span>
            {typeof value === 'boolean' ? (
              <select value={String(value)} onChange={e => setField(field, e.target.value)}>
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : (
              <input type="text" value={String(value)} onChange={e => setField(field, e.target.value)} />
            )}
          </div>
        ))}
      </div>
      <div className="cs-entry-actions">
        <button className="cs-save-btn" onClick={() => onConfirm(fields)}>Create</button>
        <button className="cs-cancel-btn" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
