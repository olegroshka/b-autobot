export type ServiceStatus = 'RUNNING' | 'STOPPED' | 'FAILED'

export interface Deployment {
  name:         string
  status:       ServiceStatus
  version:      string
  environment:  string
  host:         string
  port:         number
  team:         string
  lastDeployed: string   // ISO-8601 timestamp
  build:        string
  uptime:       string   // human-readable, e.g. "5d 2h" or "-"
}
