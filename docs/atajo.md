# Atajo de iPhone

El atajo **Gastos** registra un gasto en el hogar cada vez que se paga con Apple Pay, sin abrir la
aplicación. También se puede ejecutar a mano para cargar un gasto en efectivo o por transferencia.

Usa la captura rápida de la API (M6, RF-30 a RF-32) con un token de cliente máquina (RF-07): el
gasto queda a nombre de quien emitió el token, en su hogar, con la categoría que resuelven las
reglas del hogar.

| | |
|---|---|
| **Instalación** | [Enlace de iCloud](https://www.icloud.com/shortcuts/2d36a066c057443ea1312e72540bea31) |
| **Respaldo** | [`integraciones/ios/Gastos.shortcut`](../integraciones/ios/Gastos.shortcut), el mismo atajo, firmado por Apple |
| **Requisitos** | iPhone con iOS 27 o posterior y una cuenta en Gastos |

Hace falta iOS 27 porque el atajo guarda el token con las acciones de contenido almacenado, que
aparecieron en esa versión.

Los mismos pasos, resumidos y con capturas, están en el cliente web, en **Hogar →
Automatizaciones**, junto al formulario para emitir el token.

## 1. Instalación

1. Abrir el enlace de iCloud en el iPhone y tocar **Agregar atajo**. Sin el enlace, el archivo
   `Gastos.shortcut` se abre igual: pasándolo por AirDrop o desde la app Archivos.
2. En el cliente web, ir a **Hogar → Automatizaciones**, emitir un token con un nombre que
   identifique el teléfono y tocar **Copiar token**. El token se muestra una sola vez.
3. Ejecutar el atajo **a mano**, desde la app Atajos. Al no tener token, lo pide: propone lo que
   haya en el portapapeles. Lo comprueba contra `GET /api/capture/connection`, sin registrar ningún
   gasto, y si sirve lo guarda y avisa **"Conectado como _persona_ a _hogar_"**. Esta primera
   ejecución no registra un gasto. Después conviene copiar cualquier otra cosa: el token sigue en el
   portapapeles hasta que se lo reemplaza, y con el Portapapeles universal de Apple pasa también
   entre la computadora y el teléfono.
4. En la app Atajos, ir a **Automatización**: la automatización del atajo, con el disparador de
   Wallet "Transacción", ya aparece, desactivada. Activarla.

El orden importa: con la automatización activada antes de guardar el token, el primer pago la
dispara sin token, y el atajo lo pide en ese momento en lugar de registrar el gasto.

## 2. Uso

- **Con Apple Pay:** al pagar, la automatización ejecuta el atajo con la transacción de Wallet. El
  importe y el comercio salen de ahí, y el medio de pago es `Tarjeta`.
- **A mano:** el atajo pide el importe, el comercio y el medio de pago (`Efectivo`, `Tarjeta` o
  `Transferencia`). El importe se puede escribir como se lee: `1.234,56`, `1,234.56` o `1234`.

El servidor normaliza el importe, pone la fecha del día y resuelve la categoría con las reglas del
hogar. Si ninguna regla coincide, el gasto queda en la bandeja de no categorizados.

## 3. Notificaciones

| Notificación | Qué pasó |
|---|---|
| "Conectado como _persona_ a _hogar_" | El token sirve y quedó guardado. |
| "No se registró el token: _motivo_" | El servidor rechazó el token, por ejemplo porque está revocado. No se guardó. |
| "Gasto registrado", con importe, comercio y categoría | El gasto está en el hogar. Sin categoría, quedó en la bandeja. |
| "No se registró el gasto: _motivo_" | El servidor rechazó la captura, por ejemplo por un importe que no puede interpretar. El gasto no se guardó. |
| "No se pudo conectar con el servidor" | Sin respuesta de la API, o una respuesta que no es de la API. El gasto no se guardó. |

Si el servidor responde que el token no es válido (`401`), por ejemplo porque se revocó desde la
pantalla del hogar, el atajo lo borra del teléfono, y la próxima ejecución lo vuelve a pedir.

## 4. El token en el teléfono

El atajo guarda el token con **Almacenar contenido**, con la clave `Gastos/token`, como un valor
propio de este atajo: otros atajos no lo pueden leer. El token no viaja con el atajo: ni el enlace
de iCloud ni el archivo del repositorio lo contienen, y cada persona configura el suyo.

Para cortar el acceso de un teléfono, se revoca su token desde **Hogar → Automatizaciones**. Cambiarse
de hogar también revoca los tokens del hogar anterior (RN-18).

## 5. Revisar el atajo

El archivo está firmado y no se lee como texto en un PR. Lo que hace se puede comprobar así:

- **Es el mismo archivo que el enlace.** El SHA-256 de `Gastos.shortcut` es
  `78ccc5a1b59483d2d7a95f2204d6b5ee776431e842536aed49fd337f44af17aa`, el mismo del archivo firmado
  que descarga el enlace de iCloud. Se comprueba con `sha256sum integraciones/ios/Gastos.shortcut`.
- **Solo habla con la API.** Las dos acciones **Obtener contenido de URL** van por `https://` a la
  API desplegada: `GET /api/capture/connection` para comprobar el token y `POST /api/capture` para
  registrar el gasto. No hay otra acción que haga pedidos.
- **Solo borra el token ante un `401`.** La única acción **Eliminar contenido almacenado** está
  dentro de la condición de respuesta `401`. Cualquier otro error avisa y deja el token como está.
- **Quién lo cambia.** El enlace es de la cuenta de iCloud de Patricio, y solo él puede compartir
  una versión nueva. El enlace, el archivo y esta huella cambian en el repositorio con un PR, como
  cualquier otro cambio (sección 8).

## 6. Limitaciones

- **Solo iPhone, con iOS 27 o posterior.** La app Atajos no existe en Android.
- **Una sola moneda.** Wallet entrega el importe con el símbolo de la moneda de la tarjeta, y la API
  acepta solo el del peso (`$`, `AR$`, `ARS`). Un pago en otra moneda, como `€3.00`, se rechaza
  (RF-32): no se registra con un valor sin moneda que después sume en los totales del mes. Hay que
  cargarlo desde el cliente web.
- **La API puede tardar en responder.** En el plan gratuito, la API se detiene sin uso y el primer
  pedido la despierta (ver [arranque en frío](despliegue.md#arranque-en-frío)). Si tarda demasiado,
  el atajo avisa que no se pudo conectar, y el gasto hay que cargarlo a mano.
- **La firma del archivo vence.** Según iCloud, el certificado con que se firmó
  `Gastos.shortcut` vence el 1 de diciembre de 2027. A partir de esa fecha el archivo puede dejar de
  importarse, y hay que compartir el atajo de nuevo.
- **Apunta a la API desplegada.** La dirección está fija en las dos acciones **Obtener contenido de
  URL**. Para usarlo con otro despliegue, hay que cambiarla en las dos.

## 7. Usar la API desde otra automatización

El atajo es un cliente más de la captura rápida. Cualquier automatización que pueda hacer un
pedido HTTP, en otro teléfono o en una computadora, puede usar el mismo token, emitido desde
**Hogar → Automatizaciones**. Con `API` la dirección de la API:

```bash
# Comprobar a quién y a qué hogar conecta el token, sin registrar un gasto
curl -s "$API/api/capture/connection" -H "Authorization: Token <token>"
# → 200 {"ownerName":"...","householdName":"..."}

# Registrar un gasto. paymentMethod es opcional: Efectivo, Tarjeta o Transferencia
curl -s -X POST "$API/api/capture" -H "Authorization: Token <token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": "1.234,56", "merchant": "Carrefour", "paymentMethod": "Tarjeta"}'
# → 201 {"id":"...","amount":"1234.56","merchant":"Carrefour","categoryName":"Supermercado","expenseDate":"..."}
```

Un token inválido o revocado responde `401`, y un importe que no se puede interpretar, `400`, los
dos como `application/problem+json`, con el motivo en `detail`. El contrato completo está en
[modulos.md](modulos.md), en M6.

## 8. Actualizar el atajo

El enlace de iCloud y el archivo del repositorio tienen que ser el mismo atajo. Después de un
cambio, se comparte de nuevo desde la app Atajos, y se reemplazan los dos: el enlace en esta guía y
en la pantalla del hogar (`frontend/src/components/household/MachineTokens.tsx`), y el archivo, con
el que descarga iCloud firmado. También cambia el SHA-256 de la sección 5.

La pantalla del hogar lleva además dos capturas de la app Atajos (`frontend/src/assets/atajo/`): el
pedido del token y la automatización. Si el atajo cambia esos textos, o una versión nueva de iOS
cambia cómo se ven, hay que reemplazarlas. Van recortadas y sin datos personales.