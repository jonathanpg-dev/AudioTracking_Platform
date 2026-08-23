import { clsx, type ClassValue } from 'clsx'

// A single place to compose conditional Tailwind class lists -- every component uses this
// instead of ad-hoc string concatenation, so class merging behaves consistently everywhere.
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs)
}
