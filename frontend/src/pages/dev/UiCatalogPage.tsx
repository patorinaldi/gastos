import { PageHeader } from '../../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../../components/states/States.tsx'
import { Button, ButtonLink } from '../../components/ui/Button.tsx'
import { Notice } from '../../components/ui/Notice.tsx'
import { TextField } from '../../components/ui/TextField.tsx'
import { formatDate, formatDayMonth, formatMoney, formatMonth } from '../../format.ts'
import styles from './UiCatalogPage.module.css'

const COLORS = [
  'ink', 'text-secondary', 'text-tertiary', 'surface', 'surface-sunken', 'surface-muted',
  'border', 'accent', 'accent-text', 'accent-soft', 'danger', 'danger-soft',
]

/**
 * Catálogo del sistema de diseño: cada componente en sus variantes. Solo existe en desarrollo
 * (router.tsx no registra la ruta en el build de producción). Sirve de referencia al armar una
 * pantalla y para revisar a simple vista un cambio en los tokens.
 */
export function UiCatalogPage() {
  return (
    <>
      <PageHeader eyebrow="Solo desarrollo" title="Sistema de diseño" />

      <section className={styles.section}>
        <h2 className={styles.heading}>Colores</h2>
        <div className={styles.grid}>
          {COLORS.map((name) => (
            <span key={name} className={styles.swatch}>
              <span className={styles.chip} style={{ background: `var(--color-${name})` }} />
              --color-{name}
            </span>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.heading}>Botones</h2>
        <div className={styles.row}>
          <Button>Guardar</Button>
          <Button variant="secondary">Cancelar</Button>
          <Button variant="link">Revisar</Button>
          <Button disabled>Deshabilitado</Button>
          <ButtonLink to="/" variant="secondary">
            Enlace con forma de botón
          </ButtonLink>
        </div>
        <Button size="large">
          <span aria-hidden="true">+</span> Añadir gasto
        </Button>
      </section>

      <section className={styles.section}>
        <h2 className={styles.heading}>Campos</h2>
        <div className={styles.grid}>
          <TextField label="Correo" type="email" placeholder="ana@ejemplo.com" />
          <TextField label="Contraseña" type="password" hint="Al menos 8 caracteres." />
          <TextField label="Importe" inputMode="decimal" defaultValue="12,5" error="Ingresá un importe mayor que cero." />
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.heading}>Avisos</h2>
        <Notice action={<Button variant="link">Revisar</Button>}>3 gastos sin categorizar este mes</Notice>
        <Notice tone="danger">No pudimos guardar el gasto. Intentá de nuevo en unos segundos.</Notice>
      </section>

      <section className={styles.section}>
        <h2 className={styles.heading}>Estados (RNF-16)</h2>
        <div className={styles.grid}>
          <div className={styles.box}>
            <LoadingState />
          </div>
          <div className={styles.box}>
            <EmptyState
              title="Sin gastos este mes"
              description="Cuando registres un gasto, va a aparecer acá."
              action={<Button>Añadir gasto</Button>}
            />
          </div>
          <div className={styles.box}>
            <ErrorState onRetry={() => undefined} />
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.heading}>Formato</h2>
        <p className={styles.amount}>
          {formatMoney('1234.56')} · {formatMoney('154320.00')} · {formatMoney('0.50')}
        </p>
        <p>
          {formatDate('2026-09-21')} · {formatDayMonth('2026-09-21')} · {formatMonth('2026-09')}
        </p>
      </section>
    </>
  )
}