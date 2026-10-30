import { useState } from 'react'
import { listCategories } from '../api/categories.ts'
import { listExpenses } from '../api/expenses.ts'
import { InboxGroup } from '../components/inbox/InboxGroup.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { useRequest } from '../hooks/useRequest.ts'
import { groupByMerchant } from '../inbox.ts'
import { UNCATEGORIZED } from '../types/api.ts'
import styles from './InboxPage.module.css'

/**
 * Cuántos gastos de la bandeja se piden: el tope de una página de la API (RNF-12). Una bandeja más
 * grande se va vaciando: al clasificar, aparecen los siguientes.
 */
const INBOX_SIZE = 100

/**
 * Bandeja de no categorizados (RF-22, RF-23, HU-11): los gastos que ninguna regla reconoció, de
 * cualquier mes, agrupados por comercio. Clasificar un comercio crea, por defecto, una regla que
 * también clasifica los próximos.
 */
export function InboxPage() {
  // Cambia después de cada clasificación, y con ella se vuelve a pedir la bandeja.
  const [version, setVersion] = useState(0)
  const [openGroup, setOpenGroup] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  // Sin período, la bandeja trae toda la historia: es trabajo pendiente, no el gasto de un mes.
  const inbox = useRequest(
    `inbox#${version}`,
    () => listExpenses({ categoryId: UNCATEGORIZED, size: INBOX_SIZE }),
    'No pudimos cargar la bandeja.',
  )
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')

  const finish = (message: string) => {
    setDone(message)
    setOpenGroup(null)
    setVersion((v) => v + 1)
  }

  return (
    <>
      <PageHeader title="Bandeja" />
      <p className={styles.lede}>
        Los gastos que ninguna regla reconoció. Al clasificar un comercio, el sistema lo recuerda y los próximos
        gastos de ese comercio se clasifican solos.
      </p>

      {/* Una región que existe desde el principio, para que el lector de pantalla anuncie el resultado. */}
      <div role="status" className={styles.status}>
        {done && <Notice>{done}</Notice>}
      </div>

      {inbox.kind === 'loading' || categories.kind === 'loading' ? (
        <LoadingState label="Cargando la bandeja…" />
      ) : inbox.kind === 'failed' ? (
        <ErrorState message={inbox.message} onRetry={inbox.retry} />
      ) : categories.kind === 'failed' ? (
        <ErrorState message={categories.message} onRetry={categories.retry} />
      ) : inbox.data.totalElements === 0 ? (
        <EmptyState
          title="La bandeja está vacía"
          description="Todos los gastos tienen categoría. Los que ninguna regla reconozca van a aparecer acá."
          action={
            <ButtonLink variant="secondary" to="/expenses">
              Ver movimientos
            </ButtonLink>
          }
        />
      ) : (
        <div className={styles.groups}>
          {inbox.data.totalElements > inbox.data.content.length && (
            <p className={styles.more}>
              Se ven los {inbox.data.content.length} más recientes de {inbox.data.totalElements}. Al clasificarlos
              aparecen los siguientes.
            </p>
          )}
          {groupByMerchant(inbox.data.content).map((group) => (
            <InboxGroup
              key={group.key}
              group={group}
              categories={categories.data}
              inbox={inbox.data.content}
              open={openGroup === group.key}
              onOpen={() => {
                setOpenGroup(group.key)
                setDone(null)
              }}
              onClose={() => setOpenGroup(null)}
              onDone={finish}
            />
          ))}
        </div>
      )}
    </>
  )
}
