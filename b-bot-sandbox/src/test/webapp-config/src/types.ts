export type ConfigValue = string | number | boolean

export interface ConfigEntry {
  key:   string
  value: Record<string, ConfigValue>
}
