import { useEffect, useRef, useState } from 'react'
import { errorMessage } from '../api/client.ts'

export type RequestState<T> =
  | { kind: 'loading' }
  | { kind: 'loaded'; data: T }
  | { kind: 'failed'; message: string; retry: () => void }

type Result<T> = { request: string; kind: 'loaded'; data: T } | { request: string; kind: 'failed'; message: string }

/**
 * Un pedido al servidor y sus tres estados (RNF-16).
 *
 * `key` identifica lo que se pide: cuando cambia, el pedido se repite. Tiene que incluir todo lo que
 * use `load`, porque `load` se llama solo cuando cambia `key` o se reintenta.
 *
 * El resultado guarda el pedido al que responde. Si es de otro pedido, el estado es "cargando": no hace
 * falta reiniciarlo a mano, y una respuesta que llega tarde, de un pedido anterior, se descarta.
 *
 * @param fallback el mensaje de error cuando el servidor no da uno que sirva al usuario
 */
export function useRequest<T>(key: string, load: () => Promise<T>, fallback: string): RequestState<T> {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result<T> | null>(null)
  const request = `${key}#${attempt}`

  // load y fallback son funciones y textos nuevos en cada render: el pedido no depende de ellos sino de
  // key. Se guardan en un ref, que el efecto de abajo lee ya actualizado porque corre después.
  const latest = useRef({ load, fallback })
  useEffect(() => {
    latest.current = { load, fallback }
  })

  useEffect(() => {
    let active = true
    const { load, fallback } = latest.current
    load().then(
      (data) => active && setResult({ request, kind: 'loaded', data }),
      (error: unknown) => active && setResult({ request, kind: 'failed', message: errorMessage(error, fallback) }),
    )
    return () => {
      active = false
    }
  }, [request])

  if (result?.request !== request) {
    return { kind: 'loading' }
  }
  if (result.kind === 'failed') {
    return { kind: 'failed', message: result.message, retry: () => setAttempt((n) => n + 1) }
  }
  return { kind: 'loaded', data: result.data }
}