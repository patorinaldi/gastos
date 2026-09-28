# Documentación

Documentación técnica e informes de todas las entregas del proyecto **Gastos**.

## Documentación del sistema

| Documento | Contenido |
|---|---|
| [requerimientos.md](requerimientos.md) | Requisitos funcionales (RF), no funcionales (RNF) y de dominio (RD), con prioridad, estado y matriz de trazabilidad. |
| [reglas-de-negocio.md](reglas-de-negocio.md) | Las 17 reglas del dominio (RN), dónde se hace cumplir cada una y por qué. |
| [modulos.md](modulos.md) | Definición funcional de M1 a M8: responsabilidad, contratos de la API, decisiones de diseño y dependencias. |
| [modelo-de-datos.md](modelo-de-datos.md) | Descripción de cada tabla, sus restricciones e índices, y el mecanismo de aislamiento entre hogares. |
| [casos-de-uso.md](casos-de-uso.md) | Los 8 flujos críticos con flujo principal, alternativos y de excepción. |
| [historias-de-usuario.md](historias-de-usuario.md) | Las 20 historias (INVEST) con criterios de aceptación en formato Given-When-Then. |
| [der/](der/) | Diagrama entidad-relación completo: [código DBML](der/DER-gastos-code.md) y [diagrama exportado](der/DER-gastos.svg). |

## Entregas

| Documento | Contenido |
|---|---|
| [entrega-1-propuesta.md](entrega-1-propuesta.md) | Propuesta de proyecto de la 1.ª entrega. Problema, alcance, stack, arquitectura, estrategia de datos y seguridad, plan de trabajo y riesgos. |

## Cómo leer esta documentación

Según lo que se busque:

- **Qué hace el sistema** → [requerimientos.md](requerimientos.md), y
  [historias-de-usuario.md](historias-de-usuario.md) para la perspectiva de quien lo usa.
- **Cómo se comporta paso a paso** → [casos-de-uso.md](casos-de-uso.md).
- **Qué no puede violarse nunca** → [reglas-de-negocio.md](reglas-de-negocio.md).
- **Cómo está construido** → [modulos.md](modulos.md).
- **Cómo se guardan y se aíslan los datos** → [modelo-de-datos.md](modelo-de-datos.md).

Los documentos se referencian entre sí mediante identificadores estables: `RF-xx` requisito
funcional, `RNF-xx` no funcional, `RD-xx` de dominio, `RN-xx` regla de negocio, `HU-xx` historia de
usuario, `CU-xx` caso de uso, `Mx` módulo.
