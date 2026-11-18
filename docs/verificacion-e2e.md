# Verificación de punta a punta

Proyecto Gastos. Trabajo Final Integrador, TUP UTN.

El recorrido completo sobre el entorno desplegado, que la propuesta fija como primer criterio de
éxito: registro, verificación, invitación de un segundo integrante, carga de gastos y consulta del
análisis (iteración 5, tarea 8). Es la evidencia que cita el §12 del
[informe final](entrega-final.md).

| | |
|---|---|
| **Cliente web** | [https://gastos-bq8.pages.dev](https://gastos-bq8.pages.dev) |
| **API** | `https://gastos-api-rrr-d2eyhtcmcabudrgk.canadacentral-01.azurewebsites.net` |
| **Correo** | SMTP de Gmail |

Cada paso dice qué hacer y qué tiene que pasar. Quien lo recorre completa la columna de resultado
con ✅ o ❌, y si falla, lo anota en la [sección 4](#4-problemas-encontrados).

## 1. Chequeos automatizados

Hechos el 5 de noviembre de 2026, con `curl`, sin crear datos.

| # | Qué se comprueba | Cómo | Resultado |
|---|---|---|---|
| 1.1 | El cliente web está publicado | `GET /` responde `200` con la app (`<title>Gastos</title>`) | ✅ |
| 1.2 | El cliente web apunta a la API de producción (`VITE_API_URL`) | El JavaScript publicado contiene la dirección de la API | ✅ |
| 1.3 | Las rutas del cliente web resuelven en el navegador | `GET /verify-email` responde `200` con la app, no un 404 | ✅ |
| 1.4 | La API está arriba | `GET /actuator/health` → `{"status":"UP"}`, en menos de 1 s | ✅ |
| 1.5 | CORS acepta al cliente web | `OPTIONS /api/auth/login` con `Origin: https://gastos-bq8.pages.dev` → `200` y ese origen en `Access-Control-Allow-Origin` | ✅ |
| 1.6 | CORS rechaza otros orígenes | El mismo pedido con otro origen → `403` | ✅ |
| 1.7 | El envío de correos es real, no por consola | `POST /api/auth/password/forgot` con un correo sin cuenta → `202`; con `EMAIL_SENDER=console` sería `503` | ✅ |
| 1.8 | Los errores salen en el formato estandarizado | `GET /api/auth/me` sin sesión → `401` `application/problem+json`, con el detalle en español | ✅ |

**Observación.** El primer pedido, con la API detenida por falta de uso, no respondió en 60 segundos;
los siguientes respondieron en menos de 1 segundo. La [guía de despliegue](despliegue.md) estima
el arranque en frío en 15 a 30 segundos: conviene abrir el cliente web un rato antes de una
demostración.

## 2. Recorrido en el navegador

Hacen falta **dos personas con dos casillas de correo reales**, A y B, porque los enlaces de
verificación llegan por correo. Conviene usar nombres que identifiquen la prueba, por ejemplo
"Ana E2E" y "Beto E2E": la aplicación no permite borrar cuentas, y los datos de la prueba quedan.

| # | Quién | Qué hacer | Qué tiene que pasar | Requisito | Resultado |
|---|---|---|---|---|---|
| 2.1 | A | En el cliente web, *Crear cuenta* con nombre, correo y contraseña | Pide revisar la casilla. Iniciar sesión todavía responde que falta confirmar el correo | RF-01, RF-03, RN-02 | |
| 2.2 | A | Abrir el enlace del correo de confirmación | "Correo confirmado". El enlace lleva a `/verify-email` y el token desaparece de la dirección | RF-03, HU-02 | |
| 2.3 | A | Volver a abrir el mismo enlace | "El enlace no es válido", con la opción de pedir otro | RN-03 | |
| 2.4 | A | Iniciar sesión | Entra al tablero del mes en curso, vacío | RF-04, RF-05 | |
| 2.5 | A | *Nuevo gasto*: `12500` en `Carrefour` | Lo guarda en *Supermercado*, por las reglas del catálogo inicial | RF-13, RF-20, RF-19 | |
| 2.6 | A | *Nuevo gasto*: `3000` en `Kiosco Pepito` | Lo guarda sin categoría y avisa que quedó en la bandeja | RF-21 | |
| 2.7 | A | *Bandeja*: clasificar `Kiosco Pepito` en una categoría, creando la regla | El gasto sale de la bandeja. Un gasto nuevo en `Kiosco Pepito` ya se clasifica solo | RF-22, RF-23 | |
| 2.8 | B | Crear su cuenta, confirmar el correo e iniciar sesión | Su tablero está vacío: no ve nada del hogar de A | RNF-01 | |
| 2.9 | A | *Hogar*: generar un código de invitación y pasárselo a B | Muestra el código y su vencimiento, a 7 días | RF-08 | |
| 2.10 | B | *Hogar → Unirte a otro hogar*: ingresar el código | Muestra a qué hogar se une y que su hogar actual queda archivado, y pide confirmar | RF-09, RN-18 | |
| 2.11 | B | Confirmar | Integra el hogar de A. El tablero muestra los gastos de A | RF-09 | |
| 2.12 | A | *Hogar*: ver los integrantes | Aparecen A y B | RF-10 | |
| 2.13 | B | Volver a usar el mismo código | Responde que el código no es válido | RN-05 | |
| 2.14 | B | *Nuevo gasto*: `8000` en `Shell`, pago con *Tarjeta* | Lo guarda en *Transporte*, a nombre de B | RF-13, RF-14 | |
| 2.15 | A | *Movimientos*: filtrar por persona B y por medio de pago *Tarjeta* | Solo aparece el gasto de B en `Shell` | RF-17, RF-18 | |
| 2.16 | A | *Movimientos*: editar el gasto de `Shell` y cambiar el importe; después, eliminar otro gasto | El cambio se ve en el listado y en los totales. El eliminado deja de contar | RF-15, RF-16, RN-16 | |
| 2.17 | A | *Tablero*: mirar el total, la distribución por categoría, persona y medio de pago, y la evolución mensual | El total es la suma de los gastos vigentes. Cada desglose suma lo mismo que el total | RF-25 a RF-27, RF-34 a RF-36 | |
| 2.18 | A | *Tablero*: tocar una porción del anillo, y después un mes en la evolución | El resto de la pantalla se filtra por esa porción, y después pasa a ese mes | RF-29, RF-37 | |
| 2.19 | A | *Hogar → Automatizaciones*: emitir un token con su contraseña, copiarlo y capturar un gasto con él, con los comandos de la [sección 3](#3-captura-rápida) | El token se muestra una sola vez. La captura responde `201` con el importe normalizado (`1234.56`) y la categoría por nombre. El gasto aparece en el tablero, a nombre de A, y el token, en *Tus tokens* con la fecha de su último uso | RF-07, RF-30 a RF-32, HU-17 | |
| 2.20 | A | *Hogar → Automatizaciones*: revocar el token y volver a capturar | El token figura como revocado, y la captura responde `401` | RF-07, HU-17 | |
| 2.21 | B | Cerrar sesión. *¿Olvidaste tu contraseña?*, abrir el enlace del correo y elegir una contraseña nueva | "Contraseña cambiada". Inicia sesión con la nueva, y la anterior ya no sirve | RF-06, HU-04 | |

## 3. Captura rápida

El token se emite y se revoca desde *Hogar → Automatizaciones* (pasos 2.19 y 2.20). La captura es
lo que hace el atajo del iPhone en cada pago; para la prueba se llama a la API directamente, así no
hace falta un iPhone con iOS 27. Con `API` la dirección de la API y `<gst_...>` el token copiado:

```bash
API=https://gastos-api-rrr-d2eyhtcmcabudrgk.canadacentral-01.azurewebsites.net

# 1. Comprobar a qué hogar conecta, sin registrar nada
curl -s "$API/api/capture/connection" -H "Authorization: Token <gst_...>"

# 2. Capturar un gasto, con el importe en formato local
curl -s -X POST "$API/api/capture" -H "Authorization: Token <gst_...>" \
  -H "Content-Type: application/json" -d '{"amount": "1.234,56", "merchant": "Carrefour"}'
```

Después de revocar el token (paso 2.20), repetir el comando 2: tiene que responder `401`.

## 4. Problemas encontrados

| # | Paso | Qué pasó | Gravedad | Estado |
|---|---|---|---|---|
| 4.1 | 2.19 | El cliente web no tenía una pantalla para emitir, listar ni revocar tokens de cliente máquina. La API los implementaba (RF-07), pero sin la pantalla, configurar el atajo del teléfono exigía usar la API directamente | Media: la HU-17 no se podía completar desde la interfaz | Resuelto en #68: *Hogar → Automatizaciones* emite, lista y revoca los tokens |
| 4.2 | 1.4 | El arranque en frío superó los 60 segundos, el doble de lo que estima la guía | Baja | Abierto |
