import { describe, expect, it } from 'vitest'
import { sortByName, withCategory, withoutCategory } from './catalog.ts'

const supermercado = { id: '1', name: 'Supermercado' }
const educacion = { id: '2', name: 'Educación' }
const transporte = { id: '3', name: 'Transporte' }

describe('catálogo de categorías', () => {
  it('ordena por nombre sin distinguir mayúsculas ni tildes', () => {
    const ropa = { id: '4', name: 'ropa' }
    const entretenimiento = { id: '5', name: 'Entretenimiento' }

    expect(sortByName([supermercado, ropa, entretenimiento, educacion]).map((c) => c.name)).toEqual([
      'Educación',
      'Entretenimiento',
      'ropa',
      'Supermercado',
    ])
  })

  it('suma una categoría nueva en su lugar', () => {
    const mascotas = { id: '6', name: 'Mascotas' }

    expect(withCategory([educacion, supermercado, transporte], mascotas)).toEqual([
      educacion,
      mascotas,
      supermercado,
      transporte,
    ])
  })

  it('un renombrado reemplaza a la anterior y la reubica', () => {
    const almacen = { id: '1', name: 'Almacén' }

    expect(withCategory([educacion, supermercado, transporte], almacen)).toEqual([almacen, educacion, transporte])
  })

  it('saca la categoría dada de baja', () => {
    expect(withoutCategory([educacion, supermercado, transporte], '1')).toEqual([educacion, transporte])
  })

  it('no modifica la lista que recibe', () => {
    const list = [supermercado, educacion]

    sortByName(list)
    withCategory(list, transporte)

    expect(list).toEqual([supermercado, educacion])
  })
})
