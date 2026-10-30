import { matchesPattern, patternKey } from './patterns.ts'
import type { ExpenseResponse } from './types/api.ts'

/** Los gastos de la bandeja de un mismo comercio, que se clasifican juntos. */
export interface MerchantGroup {
  /** La clave de comparación del comercio: agrupa "Almacén de Pepe" con "ALMACEN DE PEPE". */
  key: string
  /** Cómo se muestra: como está escrito en el gasto más reciente. */
  merchant: string
  expenses: ExpenseResponse[]
}

/**
 * Agrupa la bandeja por comercio (HU-11): una regla se crea para un comercio, no para un gasto. Los
 * grupos quedan en el orden del listado, así que el primero es el del gasto más reciente. Un comercio
 * sin letras ni números, que no admite regla, va solo con los que se escriben igual.
 */
export function groupByMerchant(expenses: ExpenseResponse[]): MerchantGroup[] {
  const groups = new Map<string, MerchantGroup>()
  for (const expense of expenses) {
    const key = patternKey(expense.merchant) || expense.merchant
    const group = groups.get(key)
    if (group) {
      group.expenses.push(expense)
    } else {
      groups.set(key, { key, merchant: expense.merchant, expenses: [expense] })
    }
  }
  return [...groups.values()]
}

/** Cuántos de los gastos coinciden con el patrón: los que una regla nueva sacaría de la bandeja. */
export function countMatches(expenses: ExpenseResponse[], pattern: string): number {
  return expenses.filter((expense) => matchesPattern(expense.merchant, pattern)).length
}
