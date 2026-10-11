export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
}

export type OrderStatus = 'CREATED' | 'PAID'

export interface Order {
  id: string
  status: OrderStatus
}

export interface OrderPage {
  content: Order[]
  page: number
  size: number
  totalElements: number
}

export interface MetricNamesResponse {
  names: string[]
}

export interface MetricMeasurement {
  statistic: string
  value: number
}

export interface MetricDetails {
  name: string
  description?: string
  baseUnit?: string
  measurements: MetricMeasurement[]
}
