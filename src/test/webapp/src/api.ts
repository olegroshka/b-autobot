/**
 * Fetch wrappers for the PT-Blotter REST API.
 *
 * All functions use relative URLs so they work both in:
 * - Test mode: Playwright navigates to http://localhost:WIREMOCK_PORT/blotter/
 *   and all /api/* calls go to the same WireMock server.
 * - Dev mode: Vite proxies /api to the WireMock port (configured in vite.config.ts).
 */

export interface InquiryRequest {
  isin:        string
  description: string
  notional:    number
  side:        'BUY' | 'SELL'
  client:      string
}

export interface InquiryResponse {
  inquiry_id: string
  isin:       string
  status:     'PENDING'
  description?: string
  notional?:    number
  side?:        'BUY' | 'SELL'
  client?:      string
}

export interface QuoteRequest {
  price:        number
  spread:       number
  sent_price:   number
  sent_spread:  number
  ref_source:   string
  convention:   string
  skew:         number
}

export interface QuoteResponse {
  inquiry_id:  string
  status:      'QUOTED'
  sent_price:  number
  sent_spread: number
  timestamp:   string
}

/** POST /api/inquiry — submits a new inquiry; returns 201 on success. */
export async function submitInquiry(req: InquiryRequest): Promise<InquiryResponse> {
  const res = await fetch('/api/inquiry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const body = await res.text()
    throw Object.assign(new Error(`POST /api/inquiry failed: ${res.status}`), {
      status: res.status,
      body,
    })
  }
  return res.json() as Promise<InquiryResponse>
}

/** GET /api/inquiries — returns all current inquiries. */
export async function fetchInquiries(): Promise<InquiryResponse[]> {
  const res = await fetch('/api/inquiries')
  if (!res.ok) throw new Error(`GET /api/inquiries failed: ${res.status}`)
  return res.json() as Promise<InquiryResponse[]>
}

/** POST /api/inquiry/{id}/release — releases a quoted inquiry to RELEASED status. */
export async function releasePt(inquiryId: string): Promise<void> {
  const res = await fetch(`/api/inquiry/${inquiryId}/release`, { method: 'POST' })
  if (!res.ok) {
    const body = await res.text()
    throw Object.assign(new Error(`POST /api/inquiry/${inquiryId}/release failed: ${res.status}`), {
      status: res.status,
      body,
    })
  }
}

/** POST /api/inquiry/{id}/quote — sends a quote for an existing inquiry. */
export async function sendQuote(inquiryId: string, req: QuoteRequest): Promise<QuoteResponse> {
  const res = await fetch(`/api/inquiry/${inquiryId}/quote`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const body = await res.text()
    throw Object.assign(new Error(`POST /api/inquiry/${inquiryId}/quote failed: ${res.status}`), {
      status: res.status,
      body,
    })
  }
  return res.json() as Promise<QuoteResponse>
}
