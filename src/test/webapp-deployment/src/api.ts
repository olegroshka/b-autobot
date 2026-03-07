import type { Deployment } from './types'

/** Reads the API base from ?apiUrl= or defaults to same origin. */
function apiBase(): string {
  const params = new URLSearchParams(window.location.search)
  return params.get('apiUrl') ?? ''
}

export async function fetchDeployments(): Promise<Deployment[]> {
  const res = await fetch(`${apiBase()}/api/deployments`)
  if (!res.ok) throw new Error(`GET /api/deployments failed: ${res.status}`)
  return res.json() as Promise<Deployment[]>
}
