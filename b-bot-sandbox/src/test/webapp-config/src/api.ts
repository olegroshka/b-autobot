import type { ConfigValue } from './types'

/** Reads the API base from ?configApiUrl= or defaults to same origin. */
function apiBase(): string {
  const params = new URLSearchParams(window.location.search)
  return params.get('configApiUrl') ?? ''
}

export async function fetchNamespaces(): Promise<string[]> {
  const res = await fetch(`${apiBase()}/api/config`)
  if (!res.ok) throw new Error(`GET /api/config failed: ${res.status}`)
  return res.json() as Promise<string[]>
}

export async function fetchTypes(ns: string): Promise<string[]> {
  const res = await fetch(`${apiBase()}/api/config/${ns}`)
  if (!res.ok) throw new Error(`GET /api/config/${ns} failed: ${res.status}`)
  return res.json() as Promise<string[]>
}

export async function fetchConfig(
  ns: string,
  type: string,
): Promise<Record<string, Record<string, ConfigValue>>> {
  const res = await fetch(`${apiBase()}/api/config/${ns}/${type}`)
  if (!res.ok) throw new Error(`GET /api/config/${ns}/${type} failed: ${res.status}`)
  return res.json() as Promise<Record<string, Record<string, ConfigValue>>>
}

export async function saveConfig(
  ns: string,
  type: string,
  key: string,
  value: Record<string, ConfigValue>,
): Promise<void> {
  const res = await fetch(`${apiBase()}/api/config/${ns}/${type}/${key}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(value),
  })
  if (!res.ok) throw new Error(`PUT /api/config/${ns}/${type}/${key} failed: ${res.status}`)
}

export async function deleteConfig(ns: string, type: string, key: string): Promise<void> {
  const res = await fetch(`${apiBase()}/api/config/${ns}/${type}/${key}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`DELETE /api/config/${ns}/${type}/${key} failed: ${res.status}`)
}
