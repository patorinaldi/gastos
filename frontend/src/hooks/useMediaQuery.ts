import { useCallback, useSyncExternalStore } from 'react'

/** La disposición de escritorio, con el mismo umbral que los @media de los estilos (M7 en modulos.md). */
export const DESKTOP = '(min-width: 768px)'

/**
 * Si la ventana cumple la consulta, y se vuelve a dibujar cuando deja de cumplirla, por ejemplo al
 * girar el teléfono o achicar la ventana. Para lo que CSS no alcanza a resolver: montar algo solo
 * en una de las disposiciones.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    [query],
  )
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches)
}
