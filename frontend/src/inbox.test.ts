import { describe, expect, it } from 'vitest'
import { countMatches, groupByMerchant } from './inbox.ts'
import type { ExpenseResponse } from './types/api.ts'

let next = 0

function expense(merchant: string): ExpenseResponse {
  next += 1
  return {
    id: `id-${next}`,
    amount: '100.00',
    merchant,
    expenseDate: '2026-10-20',
    paymentMethod: 'Efectivo',
    categoryId: null,
    categoryName: null,
    ownerId: 'ana',
    ownerName: 'Ana',
    createdAt: '2026-10-20T13:00:00Z',
    updatedAt: '2026-10-20T13:00:00Z',
  }
}

describe('groupByMerchant', () => {
  it('junta el mismo comercio escrito distinto, con el nombre del más reciente', () => {
    const groups = groupByMerchant([
      expense('Almacén de Pepe'),
      expense('Kiosco'),
      expense('ALMACEN DE PEPE'),
    ])
    expect(groups.map((g) => [g.merchant, g.expenses.length])).toEqual([
      ['Almacén de Pepe', 2],
      ['Kiosco', 1],
    ])
  })

  it('un comercio sin letras ni números no se junta con otro distinto', () => {
    const groups = groupByMerchant([expense('***'), expense('---')])
    expect(groups).toHaveLength(2)
  })
})

describe('countMatches', () => {
  it('cuenta los gastos que la regla sacaría de la bandeja', () => {
    const inbox = [expense('Almacén de Pepe'), expense('Almacen de Pepe 2'), expense('Pub Ernesto')]
    expect(countMatches(inbox, 'almacen de pepe')).toBe(2)
    expect(countMatches(inbox, 'uber')).toBe(0)
  })
})
