import { useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { deleteCategoryRule } from '../../api/categories.ts'
import { ApiError, errorMessage } from '../../api/client.ts'
import type { CategoryRuleResponse } from '../../types/api.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import styles from './RuleRow.module.css'

interface RuleRowProps {
  rule: CategoryRuleResponse
  /** Ya no está: se eliminó, o la había eliminado otro integrante. */
  onDeleted: (rule: CategoryRuleResponse) => void
}

/**
 * Una regla del listado y su eliminación (RF-43), con confirmación. Al pedirla, el foco pasa a la
 * confirmación; al cancelarla, vuelve al botón que la pidió. El botón aparece recién con el estado
 * nuevo: flushSync lo dibuja antes de enfocarlo.
 */
export function RuleRow({ rule, onDeleted }: RuleRowProps) {
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmButton = useRef<HTMLButtonElement>(null)
  const deleteButton = useRef<HTMLButtonElement>(null)

  const confirm = (value: boolean) => {
    flushSync(() => {
      setConfirming(value)
      setError(null)
    })
    ;(value ? confirmButton : deleteButton).current?.focus()
  }

  const remove = async () => {
    setDeleting(true)
    setError(null)
    try {
      await deleteCategoryRule(rule.id)
      onDeleted(rule)
    } catch (failure) {
      // Un 404 es una regla que otro integrante ya eliminó: el resultado es el que se pidió.
      if (failure instanceof ApiError && failure.status === 404) {
        onDeleted(rule)
        return
      }
      setError(errorMessage(failure, 'No pudimos eliminar la regla. Intentá de nuevo.'))
      setDeleting(false)
    }
  }

  return (
    <li className={styles.row}>
      <div className={styles.summary}>
        <p className={styles.pattern}>{rule.pattern}</p>
        {!confirming && (
          <Button ref={deleteButton} variant="secondary" onClick={() => confirm(true)}>
            Eliminar
          </Button>
        )}
      </div>

      {confirming && (
        <div className={styles.confirm}>
          {error && <Notice tone="danger">{error}</Notice>}
          <p className={styles.confirmText}>
            Los gastos que ya clasificó siguen en {rule.categoryName}. Los próximos de este comercio van a la bandeja si
            ninguna otra regla los reconoce.
          </p>
          <div className={styles.actions}>
            <Button ref={confirmButton} onClick={remove} disabled={deleting}>
              {deleting ? 'Eliminando…' : 'Sí, eliminar'}
            </Button>
            <Button variant="secondary" onClick={() => confirm(false)} disabled={deleting}>
              Cancelar
            </Button>
          </div>
        </div>
      )}
    </li>
  )
}
