import { describe, expect, it } from 'vitest'
import {
  AUTO_CATEGORY,
  categoryOptions,
  createRequest,
  emptyExpense,
  expenseValues,
  updateRequest,
  validateExpense,
  type ExpenseFormValues,
  type ValidExpense,
} from './expenseForm.ts'
import { UNCATEGORIZED, type ExpenseResponse } from './types/api.ts'

const SUPER = '11111111-1111-1111-1111-111111111111'
const FARMACIA = '22222222-2222-2222-2222-222222222222'

const expense: ExpenseResponse = {
  id: '33333333-3333-3333-3333-333333333333',
  amount: '1234.56',
  merchant: 'Carrefour',
  expenseDate: '2026-10-20',
  paymentMethod: 'Tarjeta',
  categoryId: SUPER,
  categoryName: 'Supermercado',
  ownerId: '44444444-4444-4444-4444-444444444444',
  ownerName: 'Ana',
  createdAt: '2026-10-20T13:00:00Z',
  updatedAt: '2026-10-20T13:00:00Z',
}

function valid(values: ExpenseFormValues, mode: 'create' | 'edit'): ValidExpense {
  const result = validateExpense(values, mode)
  if (!result.ok) {
    throw new Error(`no validó: ${JSON.stringify(result.errors)}`)
  }
  return result.expense
}

describe('validateExpense', () => {
  it('lleva el importe al formato de la API y recorta el comercio', () => {
    const values = { ...emptyExpense('2026-10-29'), amount: '1.234,5', merchant: '  Carrefour ' }
    expect(valid(values, 'create')).toEqual({
      amount: '1234.50',
      merchant: 'Carrefour',
      expenseDate: '2026-10-29',
      paymentMethod: 'Efectivo',
      category: AUTO_CATEGORY,
    })
  })

  it('marca cada campo con su error', () => {
    const result = validateExpense({ ...emptyExpense(''), amount: '0', merchant: ' ' }, 'edit')
    expect(result).toEqual({
      ok: false,
      errors: {
        amount: 'El importe tiene que ser mayor que cero',
        merchant: 'Ingresá el comercio',
        expenseDate: 'Elegí la fecha del gasto',
      },
    })
  })

  it('en el alta la fecha puede quedar vacía: la pone el servidor', () => {
    const values = { ...emptyExpense(''), amount: '10', merchant: 'Kiosco' }
    expect(valid(values, 'create').expenseDate).toBeUndefined()
  })

  it('rechaza una fecha que no tiene el formato de la API', () => {
    const result = validateExpense({ ...emptyExpense('29/10/2026'), amount: '10', merchant: 'Kiosco' }, 'create')
    expect(result.ok).toBe(false)
  })
})

describe('createRequest', () => {
  it('sin categoría elegida no la manda, para que la resuelvan las reglas', () => {
    const values = { ...emptyExpense('2026-10-29'), amount: '10', merchant: 'Kiosco' }
    expect(createRequest(valid(values, 'create'))).toEqual({
      amount: '10.00',
      merchant: 'Kiosco',
      expenseDate: '2026-10-29',
      paymentMethod: 'Efectivo',
      categoryId: undefined,
    })
  })

  it('con una categoría elegida, la manda', () => {
    const values = { ...emptyExpense('2026-10-29'), amount: '10', merchant: 'Kiosco', category: SUPER }
    expect(createRequest(valid(values, 'create')).categoryId).toBe(SUPER)
  })
})

describe('updateRequest', () => {
  it('sin cambios, la petición va vacía', () => {
    expect(updateRequest(expense, valid(expenseValues(expense), 'edit'))).toEqual({})
  })

  it('manda solo lo que cambió', () => {
    const values = { ...expenseValues(expense), amount: '1.300', paymentMethod: 'Efectivo' as const }
    expect(updateRequest(expense, valid(values, 'edit'))).toEqual({ amount: '1300.00', paymentMethod: 'Efectivo' })
  })

  it('cambia de categoría', () => {
    const values = { ...expenseValues(expense), category: FARMACIA }
    expect(updateRequest(expense, valid(values, 'edit'))).toEqual({ categoryId: FARMACIA })
  })

  it('devuelve el gasto a la bandeja con uncategorized, no con una categoría nula', () => {
    const values = { ...expenseValues(expense), category: UNCATEGORIZED }
    expect(updateRequest(expense, valid(values, 'edit'))).toEqual({ uncategorized: true })
  })

  it('un gasto que ya está en la bandeja no se vuelve a mandar a ella', () => {
    const pending = { ...expense, categoryId: null, categoryName: null }
    expect(updateRequest(pending, valid(expenseValues(pending), 'edit'))).toEqual({})
  })
})

describe('categoryOptions', () => {
  const categories = [{ id: SUPER, name: 'Supermercado' }]

  it('en el alta, la primera opción es la automática', () => {
    expect(categoryOptions(categories, 'create').map((o) => o.value)).toEqual([AUTO_CATEGORY, SUPER])
  })

  it('en la edición, la última es la bandeja', () => {
    expect(categoryOptions(categories, 'edit', expense).map((o) => o.value)).toEqual([SUPER, UNCATEGORIZED])
  })

  it('ofrece la categoría dada de baja que el gasto conserva', () => {
    const old = { ...expense, categoryId: FARMACIA, categoryName: 'Farmacia' }
    expect(categoryOptions(categories, 'edit', old)).toContainEqual({ value: FARMACIA, label: 'Farmacia (dada de baja)' })
  })
})
