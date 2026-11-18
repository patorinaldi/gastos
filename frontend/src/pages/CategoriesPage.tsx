import { useId, useRef, useState } from 'react'
import { listCategories } from '../api/categories.ts'
import { sortByName, withCategory, withoutCategory } from '../catalog.ts'
import { CategoryRow } from '../components/categories/CategoryRow.tsx'
import { NewCategoryForm } from '../components/categories/NewCategoryForm.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { useRequest } from '../hooks/useRequest.ts'
import type { CategoryResponse } from '../types/api.ts'
import styles from './CategoriesPage.module.css'

/**
 * El catálogo de categorías del hogar (RF-24, HU-12): crear, renombrar y dar de baja. Es el mismo
 * para todos los integrantes.
 */
export function CategoriesPage() {
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')

  return (
    <>
      <PageHeader title="Categorías" />
      <p className={styles.lede}>
        Las categorías con las que se clasifican los gastos del hogar. Son las mismas para todos los integrantes.
      </p>

      {categories.kind === 'loading' ? (
        <LoadingState label="Cargando las categorías…" />
      ) : categories.kind === 'failed' ? (
        <ErrorState message={categories.message} onRetry={categories.retry} />
      ) : (
        <Catalog initial={categories.data} />
      )}
    </>
  )
}

/**
 * La lista se pide una vez y se actualiza con cada respuesta del servidor (ver catalog.ts). Lo que
 * cambie otro integrante mientras tanto se ve al volver a la pantalla.
 */
function Catalog({ initial }: { initial: CategoryResponse[] }) {
  const [categories, setCategories] = useState(() => sortByName(initial))
  const [done, setDone] = useState<string | null>(null)
  // La fila dada de baja desaparece con el foco: pasa al título de la lista.
  const listTitle = useRef<HTMLHeadingElement>(null)
  const formTitleId = useId()
  const listTitleId = useId()

  const onCreated = (category: CategoryResponse) => {
    setCategories((current) => withCategory(current, category))
    setDone(`Creamos la categoría «${category.name}».`)
  }

  const onRenamed = (category: CategoryResponse) => {
    const previous = categories.find((c) => c.id === category.id)?.name
    setCategories((current) => withCategory(current, category))
    setDone(
      previous && previous !== category.name
        ? `«${previous}» ahora se llama «${category.name}», también en sus gastos anteriores.`
        : `Guardamos el nombre «${category.name}».`,
    )
  }

  const onDeactivated = (category: CategoryResponse) => {
    setCategories((current) => withoutCategory(current, category.id))
    setDone(`Dimos de baja «${category.name}». Sus gastos la conservan en los movimientos y en el tablero.`)
    listTitle.current?.focus()
  }

  return (
    <>
      {/* Una región que existe desde el principio, para que el lector de pantalla anuncie el resultado. */}
      <div role="status" className={styles.status}>
        {done && <Notice>{done}</Notice>}
      </div>

      <div className={styles.layout}>
        <section className={styles.section} aria-labelledby={formTitleId}>
          <h2 id={formTitleId} className={styles.title}>
            Nueva categoría
          </h2>
          <NewCategoryForm onCreated={onCreated} />
        </section>

        <section className={styles.section} aria-labelledby={listTitleId}>
          <h2 ref={listTitle} id={listTitleId} className={styles.title} tabIndex={-1}>
            Categorías del hogar<span className={styles.count}> · {categories.length}</span>
          </h2>
          {categories.length === 0 ? (
            <EmptyState
              title="El hogar no tiene categorías"
              description="Creá una para poder clasificar los gastos. Mientras tanto, los gastos nuevos quedan en la bandeja."
            />
          ) : (
            <ul className={styles.list}>
              {categories.map((category) => (
                <CategoryRow
                  key={category.id}
                  category={category}
                  onRenamed={onRenamed}
                  onDeactivated={onDeactivated}
                />
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
