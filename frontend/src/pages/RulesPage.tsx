import { useId, useRef, useState, type ReactNode } from 'react'
import { listCategories, listCategoryRules } from '../api/categories.ts'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { NewRuleForm } from '../components/rules/NewRuleForm.tsx'
import { RuleRow } from '../components/rules/RuleRow.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { TextField } from '../components/ui/TextField.tsx'
import { useRequest } from '../hooks/useRequest.ts'
import { groupRules } from '../rules.ts'
import type { CategoryResponse, CategoryRuleResponse } from '../types/api.ts'
import styles from './RulesPage.module.css'

/**
 * Las reglas patrón-categoría del hogar (RF-43): verlas por categoría, buscarlas, crear una y
 * eliminarlas. Eliminar una regla no cambia los gastos que ya clasificó.
 */
export function RulesPage() {
  const rules = useRequest('category-rules', listCategoryRules, 'No pudimos cargar las reglas.')
  const categories = useRequest('categories', listCategories, 'No pudimos cargar las categorías.')

  return (
    <>
      <PageHeader title="Reglas" />
      <p className={styles.lede}>
        Cada regla clasifica sola los gastos cuyo comercio contiene su patrón. Si coinciden varias, gana la del
        patrón más largo.
      </p>

      {rules.kind === 'loading' || categories.kind === 'loading' ? (
        <LoadingState label="Cargando las reglas…" />
      ) : rules.kind === 'failed' ? (
        <ErrorState message={rules.message} onRetry={rules.retry} />
      ) : categories.kind === 'failed' ? (
        <ErrorState message={categories.message} onRetry={categories.retry} />
      ) : (
        <RuleList initial={rules.data} categories={categories.data} />
      )}
    </>
  )
}

interface RuleListProps {
  initial: CategoryRuleResponse[]
  categories: CategoryResponse[]
}

/**
 * La lista se pide una vez y se actualiza con cada respuesta del servidor: volver a pedirla dibujaría
 * la carga y se llevaría el foco. Lo que cambie otro integrante mientras tanto se ve al volver.
 */
function RuleList({ initial, categories }: RuleListProps) {
  const [rules, setRules] = useState(initial)
  const [query, setQuery] = useState('')
  const [done, setDone] = useState<string | null>(null)
  // La regla eliminada desaparece con el foco: pasa al título de la lista.
  const listTitle = useRef<HTMLHeadingElement>(null)
  const formTitleId = useId()
  const listTitleId = useId()

  const groups = groupRules(rules, query)

  const onCreated = (rule: CategoryRuleResponse, reclassified: number) => {
    setRules((current) => [...current, rule])
    const moved =
      reclassified === 0
        ? 'no había gastos de ese comercio en la bandeja.'
        : reclassified === 1
          ? '1 gasto pasó de la bandeja a esa categoría.'
          : `${reclassified} gastos pasaron de la bandeja a esa categoría.`
    setDone(`Creamos la regla «${rule.pattern}» para ${rule.categoryName}: ${moved}`)
  }

  const onDeleted = (rule: CategoryRuleResponse) => {
    setRules((current) => current.filter((r) => r.id !== rule.id))
    setDone(`Eliminamos la regla «${rule.pattern}». Los gastos que ya clasificó siguen en ${rule.categoryName}.`)
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
            Nueva regla
          </h2>
          <NewRuleForm categories={categories} onCreated={onCreated} />
        </section>

        <section className={styles.section} aria-labelledby={listTitleId}>
          <h2 ref={listTitle} id={listTitleId} className={styles.title} tabIndex={-1}>
            Reglas del hogar<span className={styles.count}> · {rules.length}</span>
          </h2>

          {rules.length === 0 ? (
            <EmptyState
              title="El hogar no tiene reglas"
              description="Creá una acá, o clasificá un comercio desde la bandeja: los próximos gastos de ese comercio se clasifican solos."
              action={
                <ButtonLink variant="secondary" to="/inbox">
                  Ir a la bandeja
                </ButtonLink>
              }
            />
          ) : (
            <>
              <TextField
                label="Buscar"
                type="search"
                autoComplete="off"
                hint="Por patrón o por categoría."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              {groups.length === 0 ? (
                <EmptyState
                  title={`Ninguna regla coincide con «${query.trim()}»`}
                  description="Probá con otra parte del patrón o con el nombre de la categoría."
                />
              ) : (
                <div className={styles.groups}>
                  {groups.map((group) => (
                    <RuleGroup key={group.categoryId} name={group.categoryName}>
                      {group.rules.map((rule) => (
                        <RuleRow key={rule.id} rule={rule} onDeleted={onDeleted} />
                      ))}
                    </RuleGroup>
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </>
  )
}

function RuleGroup({ name, children }: { name: string; children: ReactNode[] }) {
  const id = useId()
  return (
    <section aria-labelledby={id}>
      <h3 id={id} className={styles.groupTitle}>
        {name}
        <span className={styles.count}> · {children.length}</span>
      </h3>
      <ul className={styles.list}>{children}</ul>
    </section>
  )
}
