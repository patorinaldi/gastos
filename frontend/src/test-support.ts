/*
 * Apoyo para las pruebas, que corren en Node: ahí no hay localStorage.
 */

/** Un localStorage en memoria. */
export class MemoryStorage implements Storage {
  private readonly items = new Map<string, string>()

  get length() {
    return this.items.size
  }

  clear() {
    this.items.clear()
  }

  getItem(key: string) {
    return this.items.get(key) ?? null
  }

  key(index: number) {
    return [...this.items.keys()][index] ?? null
  }

  removeItem(key: string) {
    this.items.delete(key)
  }

  setItem(key: string, value: string) {
    this.items.set(key, String(value))
  }
}

/** Un localStorage que el navegador no deja usar, como en el modo privado estricto. */
export class BlockedStorage extends MemoryStorage {
  override getItem(): string | null {
    throw new DOMException('Bloqueado', 'SecurityError')
  }

  override setItem() {
    throw new DOMException('Bloqueado', 'SecurityError')
  }

  override removeItem() {
    throw new DOMException('Bloqueado', 'SecurityError')
  }
}
