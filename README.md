# Gastos

**Aplicación web para el registro y análisis de gastos compartidos en unidades de convivencia.**

Cada persona se registra con su correo y al hacerlo se crea su hogar, al que invita al resto
mediante un código; desde ahí todos los gastos alimentan una misma vista compartida. Registrar
un gasto requiere solo el importe y el comercio: el sistema asigna la fecha, identifica a la
persona por sus credenciales y determina la categoría aplicando las reglas del hogar. Sobre esa
base, un tablero muestra el total del período, la distribución por categoría, persona o medio de
pago, y la evolución mensual contra la media histórica.

El aislamiento de datos entre hogares se implementa con Row-Level Security de PostgreSQL, no con
condiciones escritas a mano en cada consulta: una política del motor alcanza a todas las
consultas y falla de forma cerrada.

Trabajo Final Integrador — Tecnicatura Universitaria en Programación, UTN.

| | |
|---|---|
| **Integrantes** | Juan Francisco Reales · Elian Rivoira · Patricio Tomás Rinaldi |
| **Tutor** | Gerardo Adrián Herrera |
| **Propuesta (1.ª entrega)** | [`docs/entrega-1-propuesta.md`](docs/entrega-1-propuesta.md) |

---

## Estructura del repositorio

```
backend/     API REST en Spring Boot, organizada por capas, con sus pruebas y las
             migraciones versionadas del esquema
frontend/    Cliente web (React + TypeScript + Vite), build estático apto para CDN
docs/        Documentación técnica e informes de todas las entregas
.github/     Canalizaciones de integración y despliegue continuo
```

El backend se organiza en capas con dependencias unidireccionales, cada una conociendo solo a la
inmediatamente inferior, bajo `io.github.patorinaldi.gastos.api`:

| Capa | Contenido |
|---|---|
| `web` | Controladores y objetos de transferencia (DTOs) |
| `service` | Lógica de negocio y límites transaccionales |
| `repository` | Acceso a datos |
| `domain` | Entidades persistentes |
| `security` | Autenticación y propagación del contexto del hogar |
| `config` | Configuración transversal |

Las entidades persistentes y los objetos de transferencia son tipos distintos: el modelo interno
nunca se expone en la API.

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje backend | Java 21 (LTS) |
| Framework backend | Spring Boot 4.1 |
| Persistencia | Spring Data JPA / Hibernate |
| Seguridad | Spring Security |
| Base de datos | PostgreSQL 17 (Row-Level Security) |
| Migraciones | Flyway |
| Construcción | Maven (wrapper incluido en el repositorio) |
| Frontend | React 19 + TypeScript + Vite |
| Visualizaciones | Recharts |
| Pruebas | JUnit 5 + Testcontainers |
| CI/CD | GitHub Actions |

---

## Requisitos

| Herramienta | Versión | Necesaria para |
|---|---|---|
| JDK | 21 o superior | Compilar y ejecutar el backend |
| Node.js | 20 o superior | Cliente web |
| Docker | reciente | PostgreSQL de desarrollo y pruebas de integración |

Maven **no** hace falta instalarlo: el repositorio incluye el wrapper (`backend/mvnw`), que
descarga la versión declarada en `backend/.mvn/wrapper/maven-wrapper.properties`.

El backend compila con `release 21`, por lo que un JDK más nuevo (24, por ejemplo) también sirve
sin cambiar nada.

## Instalación y ejecución local

```bash
git clone https://github.com/patorinaldi/gastos.git
cd gastos
```

### Backend

```bash
cd backend
./mvnw spring-boot:run          # en Windows: mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. Para comprobar que arrancó:

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Compilar y ejecutar la batería de pruebas:

```bash
cd backend
./mvnw verify
```

El empaquetado deja el ejecutable en `backend/target/gastos-<versión>.jar`, que se lanza con
`java -jar`.

### Frontend

Pendiente

---

## Convenciones

- **Idioma.** Los identificadores del código se escriben en inglés; los valores de datos del
  dominio permanecen en español.
- **Esquema de base de datos.** Es propiedad exclusiva de las migraciones versionadas. La capa de
  mapeo se configura con `ddl-auto: validate`: valida que las entidades coincidan con el esquema
  existente, sin poder crearlo ni modificarlo. Si se desincronizan, la aplicación falla al
  arrancar de forma deliberada.
- **Secretos.** No se versiona ninguno —cadenas de conexión, claves de firma, credenciales del
  proveedor de correo—: se inyectan como variables de entorno del servicio.
- **Ramas y revisión.** La rama principal está protegida. El trabajo se hace en ramas de
  funcionalidad e ingresa mediante pull request con revisión de al menos otro integrante. Todo PR
  que toque migraciones, políticas RLS, el rol de base, los filtros de autenticación, la
  propagación del contexto de hogar o las pruebas de aislamiento requiere a los **otros dos**
  integrantes como revisores. Una ejecución fallida de las pruebas bloquea la incorporación.
