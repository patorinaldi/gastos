/*
 * Qué decir cuando una pantalla falla al dibujarse, en lugar de la pantalla por defecto de React Router,
 * que en producción muestra el mensaje y el stack de la excepción en inglés (RNF-17).
 *
 * Nunca se muestra el mensaje de la excepción: puede tener detalle técnico, y no le dice a la persona
 * qué hacer. El mensaje queda en la consola del navegador.
 */

export interface RouteErrorCopy {
  title: string
  description: string
}

// Lo que dice cada navegador cuando no puede cargar un módulo diferido: Chrome y Edge, Firefox, y
// Safari. Pasa cuando se publicó una versión nueva con la pestaña abierta: los archivos de la versión
// anterior ya no existen, y Pages responde index.html en su lugar.
const OUTDATED_BUILD = [
  /failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /importing a module script failed/i,
]

/** Si el error es de una versión de la app que ya no está publicada, y se arregla recargando. */
export function isOutdatedBuild(error: unknown): boolean {
  return error instanceof Error && OUTDATED_BUILD.some((pattern) => pattern.test(error.message))
}

export function routeErrorCopy(error: unknown): RouteErrorCopy {
  if (isOutdatedBuild(error)) {
    return {
      title: 'Hay una versión nueva de Gastos',
      description: 'Recargá la página para seguir con la versión actual. No se pierde nada de lo que guardaste.',
    }
  }
  return {
    title: 'Algo salió mal',
    description:
      'No pudimos mostrar esta pantalla. Recargá la página, y si vuelve a pasar, probá desde el tablero.',
  }
}
