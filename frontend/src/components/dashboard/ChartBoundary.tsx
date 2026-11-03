import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ChartBoundaryProps {
  children: ReactNode
}

interface ChartBoundaryState {
  failed: boolean
}

/**
 * Si el gráfico falla, el tablero sigue sin él, en lugar de caer entero en la pantalla de error. El
 * caso típico es el anillo, que se descarga aparte: con la pestaña abierta durante una publicación
 * nueva, su archivo ya no existe. La leyenda de al lado tiene los mismos datos y los mismos clics, así
 * que no se pierde nada que no se pueda hacer igual.
 *
 * Es una clase porque React solo permite atrapar errores de render con un componente de clase.
 */
export class ChartBoundary extends Component<ChartBoundaryProps, ChartBoundaryState> {
  state: ChartBoundaryState = { failed: false }

  static getDerivedStateFromError(): ChartBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    console.error('No se pudo dibujar el gráfico:', error, info.componentStack)
  }

  render() {
    return this.state.failed ? null : this.props.children
  }
}
