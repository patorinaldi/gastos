import { describe, expect, it } from 'vitest'
import { isOutdatedBuild, routeErrorCopy } from './routeError.ts'

describe('isOutdatedBuild', () => {
  it('reconoce el módulo diferido que no se pudo cargar, en cada navegador', () => {
    const messages = [
      'Failed to fetch dynamically imported module: https://gastos.pages.dev/assets/Donut-a1b2c3.js',
      'error loading dynamically imported module: https://gastos.pages.dev/assets/Donut-a1b2c3.js',
      'Importing a module script failed.',
    ]
    for (const message of messages) {
      expect(isOutdatedBuild(new TypeError(message)), message).toBe(true)
    }
  })

  it('un error cualquiera no es una versión vieja', () => {
    expect(isOutdatedBuild(new Error("Cannot read properties of undefined (reading 'name')"))).toBe(false)
    expect(isOutdatedBuild('Failed to fetch dynamically imported module')).toBe(false)
    expect(isOutdatedBuild(null)).toBe(false)
  })
})

describe('routeErrorCopy', () => {
  it('a una versión vieja le pide recargar', () => {
    const copy = routeErrorCopy(new TypeError('Importing a module script failed.'))
    expect(copy.title).toBe('Hay una versión nueva de Gastos')
    expect(copy.description).toContain('Recargá la página')
  })

  // RNF-17: ni el mensaje ni el tipo de la excepción llegan a la pantalla.
  it('nunca muestra el detalle de la excepción', () => {
    const error = new TypeError("Cannot read properties of undefined (reading 'categoryName')")
    const copy = routeErrorCopy(error)

    expect(copy.title).toBe('Algo salió mal')
    expect(`${copy.title} ${copy.description}`).not.toMatch(/categoryName|undefined|TypeError/)
  })
})
