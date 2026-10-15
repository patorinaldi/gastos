import type { ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router'
import styles from './Button.module.css'

type Variant = 'primary' | 'secondary' | 'link'
type Size = 'normal' | 'large'

interface StyleProps {
  /** `primary` es la acción principal de la pantalla; debería haber una sola. */
  variant?: Variant
  /** `large` ocupa todo el ancho: es la acción fija de la pantalla en móvil. */
  size?: Size
  /** Ocupa todo el ancho sin cambiar de tamaño, como en un formulario. */
  block?: boolean
}

function buttonClass({ variant = 'primary', size = 'normal', block = false }: StyleProps, extra?: string) {
  return [
    styles.button,
    styles[variant],
    size === 'large' && styles.large,
    block && styles.block,
    extra,
  ]
    .filter(Boolean)
    .join(' ')
}

type ButtonProps = StyleProps & ButtonHTMLAttributes<HTMLButtonElement>

export function Button({ variant, size, block, className, type = 'button', ...props }: ButtonProps) {
  return <button type={type} className={buttonClass({ variant, size, block }, className)} {...props} />
}

type ButtonLinkProps = StyleProps & LinkProps

/** Un enlace con aspecto de botón, para las acciones que llevan a otra pantalla. */
export function ButtonLink({ variant, size, block, className, ...props }: ButtonLinkProps) {
  return <Link className={buttonClass({ variant, size, block }, className)} {...props} />
}
