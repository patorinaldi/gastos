/**
 * Contratos de la API, tipados para el cliente.
 *
 * Espejo de los records de `backend/.../web/dto`. Los dos lados se escriben a mano y se cambian
 * juntos: generar estos tipos desde OpenAPI exige controladores, y todavía no hay ninguno.
 *
 * Reglas del contrato que estos tipos hacen cumplir:
 * - Los importes son `string`, nunca `number`. Un número JSON pasa por el punto flotante de
 *   JavaScript y pierde centavos. Para operar con ellos hay que usar decimales exactos.
 * - Los medios de pago viajan en español, que es como los guarda la base.
 * - El hogar nunca viaja en la petición: lo resuelve el servidor desde el usuario autenticado.
 */

/** Identificador opaco (UUID). */
export type Uuid = string

/** Fecha sin hora, ISO-8601: `"2026-09-21"`. */
export type IsoDate = string

/** Instante con zona, ISO-8601: `"2026-09-21T13:45:00Z"`. */
export type IsoInstant = string

/** Mes, ISO-8601: `"2026-09"`. */
export type IsoMonth = string

/** Importe decimal exacto con dos decimales, como cadena: `"1234.56"`. */
export type Money = string

export type PaymentMethod = 'Efectivo' | 'Tarjeta' | 'Transferencia'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ── M1 · Identidad y acceso ─────────────────────────────────────────────────

export interface RegisterRequest {
  name: string
  email: string
  /** Entre 8 y 72 caracteres: bcrypt ignora lo que pase de 72. */
  password: string
}

export interface RegisterResponse {
  userId: Uuid
  householdId: Uuid
  emailVerified: boolean
}

export interface VerifyEmailRequest {
  token: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  expiresAt: IsoInstant
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  password: string
}

export interface CurrentUserResponse {
  userId: Uuid
  name: string
  email: string
  emailVerified: boolean
  householdId: Uuid
  householdName: string
}

export interface CreateMachineTokenRequest {
  name: string
}

/** El `token` en claro llega solo en esta respuesta: la base guarda su hash. */
export interface CreatedMachineTokenResponse {
  id: Uuid
  name: string
  token: string
  createdAt: IsoInstant
}

export interface MachineTokenResponse {
  id: Uuid
  name: string
  createdAt: IsoInstant
  lastUsedAt: IsoInstant | null
  revokedAt: IsoInstant | null
}

// ── M2 · Hogares ────────────────────────────────────────────────────────────

export interface HouseholdResponse {
  id: Uuid
  name: string
  createdAt: IsoInstant
}

export interface RenameHouseholdRequest {
  name: string
}

export interface MemberResponse {
  id: Uuid
  name: string
  email: string
}

export interface InvitationResponse {
  code: string
  expiresAt: IsoInstant
}

export interface InvitationPreviewRequest {
  code: string
}

/** Lo que hay que advertir antes de canjear: canjear es cambiarse de hogar. */
export interface InvitationPreviewResponse {
  householdName: string
  currentHouseholdWillBeArchived: boolean
  machineTokensToRevoke: number
}

export interface RedeemInvitationRequest {
  code: string
  /** Literal `true`: el tipo no deja construir la petición sin confirmar, que el servidor rechaza con 400. */
  confirm: true
}

export interface RedeemInvitationResponse {
  householdId: Uuid
  householdName: string
  previousHouseholdArchived: boolean
}

// ── M3 · Gastos ─────────────────────────────────────────────────────────────

/** Lo mínimo es importe y comercio. La fecha, el responsable y la categoría los pone el servidor. */
export interface CreateExpenseRequest {
  amount: Money
  merchant: string
  /** Sin fecha, la del día. */
  expenseDate?: IsoDate
  /** Sin medio de pago, el servidor registra `Efectivo`. */
  paymentMethod?: PaymentMethod
  categoryId?: Uuid
}

/** Modificación parcial: lo que no se manda, no se toca. */
export interface UpdateExpenseRequest {
  amount?: Money
  merchant?: string
  expenseDate?: IsoDate
  paymentMethod?: PaymentMethod
  categoryId?: Uuid
}

export interface ExpenseResponse {
  id: Uuid
  amount: Money
  merchant: string
  expenseDate: IsoDate
  paymentMethod: PaymentMethod
  /** Nulo en los gastos de la bandeja de no categorizados. */
  categoryId: Uuid | null
  categoryName: string | null
  ownerId: Uuid
  ownerName: string
  createdAt: IsoInstant
  updatedAt: IsoInstant
}

/** Valor de `categoryId` que pide la bandeja de no categorizados. */
export const UNCATEGORIZED = 'uncategorized'

/** Filtros del listado, como parámetros de consulta. Se combinan entre sí. */
export interface ExpenseFilter {
  /** Sin período, el mes en curso. */
  from?: IsoDate
  to?: IsoDate
  /**
   * El identificador de una categoría, o `UNCATEGORIZED` para la bandeja.
   *
   * El tipo es `string` a secas y no `Uuid | typeof UNCATEGORIZED`: como `Uuid` es un alias de
   * `string`, esa unión se colapsa y no restringe nada. Lo hace cumplir el servidor, que rechaza
   * con 400 cualquier valor que no sea un identificador o `uncategorized`.
   */
  categoryId?: string
  ownerId?: Uuid
  paymentMethod?: PaymentMethod
  /** Desde 0. Sin paginación, el servidor devuelve la página 0 de 20 elementos. */
  page?: number
  /** Máximo 100. */
  size?: number
}

// ── M4 · Categorización ─────────────────────────────────────────────────────

export interface CategoryResponse {
  id: Uuid
  name: string
}

/** Sirve para crear y para renombrar. */
export interface SaveCategoryRequest {
  name: string
}

export interface CategoryRuleResponse {
  id: Uuid
  pattern: string
  categoryId: Uuid
  categoryName: string
}

export interface CreateCategoryRuleRequest {
  pattern: string
  categoryId: Uuid
}

export interface CreateCategoryRuleResponse {
  id: Uuid
  pattern: string
  categoryId: Uuid
  /** Cuántos gastos salieron de la bandeja al crear la regla. */
  reclassified: number
}

// ── M5 · Análisis ───────────────────────────────────────────────────────────

export interface CategoryTotal {
  /** Nulo: el total de los gastos sin categoría. */
  categoryId: Uuid | null
  name: string | null
  total: Money
}

export interface OwnerTotal {
  ownerId: Uuid
  name: string
  total: Money
}

export interface PaymentMethodTotal {
  paymentMethod: PaymentMethod
  total: Money
}

/**
 * Un período sin gastos devuelve `total: "0.00"` y las tres listas vacías, nunca null. Para
 * mostrar el estado vacío hay que mirar las listas, no el total.
 */
export interface SummaryResponse {
  total: Money
  byCategory: CategoryTotal[]
  byOwner: OwnerTotal[]
  byPaymentMethod: PaymentMethodTotal[]
}

export interface MonthlyPoint {
  month: IsoMonth
  total: Money
}

export interface MonthlySeriesResponse {
  months: MonthlyPoint[]
  /**
   * Media histórica, calculada solo sobre los meses con gasto registrado. Un hogar sin gastos
   * devuelve `months` vacío y `average: "0.00"`.
   */
  average: Money
}

export interface MerchantTotal {
  merchant: string
  total: Money
  expenses: number
}

// ── M6 · API de integración ─────────────────────────────────────────────────

/** El importe va como el cliente lo leyó: `"1.234,56"`, `"1,234.56"` o sin separadores. */
export interface CaptureRequest {
  amount: string
  merchant: string
}

export interface CaptureResponse {
  id: Uuid
  /** Ya normalizado, para poder verificar que se interpretó como se esperaba. */
  amount: Money
  merchant: string
  categoryId: Uuid | null
  expenseDate: IsoDate
}
