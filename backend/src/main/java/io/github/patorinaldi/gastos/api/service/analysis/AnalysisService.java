package io.github.patorinaldi.gastos.api.service.analysis;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.service.analysis.MonthlySeries.MonthAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.CategoryAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.OwnerAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.PaymentMethodAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Análisis del gasto del hogar (M5, RF-25 a RF-29).
 *
 * <p>Las agregaciones se resuelven en la base, con {@code group by}, y nunca trayendo los gastos a
 * memoria: el volumen crece con el uso (RNF-11).
 *
 * <p>Ninguna consulta nombra el hogar: lo pone RLS. Cada método es transaccional, así que
 * {@code HouseholdTransactionListener} fija el hogar de la sesión al empezar, y las políticas de
 * {@code expenses} y {@code categories} dejan fuera las filas de cualquier otro hogar. {@code users}
 * no tiene RLS, pero solo se une por el responsable de gastos que ya pasaron ese filtro. Como
 * {@code app_current_household()} es {@code stable}, el planificador usa igual los índices que
 * empiezan por {@code household_id}.
 *
 * <p>Los gastos dados de baja no cuentan (RN-16). Las categorías dadas de baja sí: sus gastos siguen
 * siendo gasto del período en que se hicieron.
 *
 * <p>Sin período, el resumen y el ranking toman el mes en curso (RD-07, CU-06), en la hora de
 * Argentina: un gasto del día 1 a la noche no puede caer en el mes anterior por mirar la fecha en
 * UTC. Los métodos con más de una consulta corren en {@code REPEATABLE_READ}, así todas ven la misma
 * foto de la base aunque otro integrante cargue un gasto en el medio.
 */
@Service
public class AnalysisService {

    private static final int MONEY_SCALE = 2;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /**
     * Zona en la que se decide qué es "el mes en curso". Es la misma con la que M3 completa la fecha
     * del gasto (RF-14): ver {@code TimeZoneConfiguration}.
     */
    private final ZoneId zone;

    public AnalysisService(NamedParameterJdbcTemplate jdbc, Clock clock, ZoneId zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * Total del período y su desglose por categoría, integrante y medio de pago (HU-13). Sin
     * período, el mes en curso (RD-07).
     *
     * <p>El total es la suma del desglose por medio de pago, que no admite nulos y tiene a lo sumo
     * tres filas: coincide siempre con los desgloses, y ahorra una pasada sobre los gastos.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(AnalysisFilter filter) {
        Where where = Where.of(withCurrentMonthByDefault(filter));

        // left join: los gastos sin categoría forman su propia fila, con id y nombre nulos.
        List<CategoryAmount> byCategory = jdbc.query("""
                        select c.id, c.name, sum(e.amount) as total
                        from expenses e
                            left join categories c on c.household_id = e.household_id and c.id = e.category_id
                        where %s
                        group by c.id, c.name
                        order by total desc, c.name nulls last""".formatted(where.sql()),
                where.params(),
                (rs, row) -> new CategoryAmount(
                        rs.getObject("id", UUID.class), rs.getString("name"), money(rs.getBigDecimal("total"))));

        // Quien se cambió de hogar sigue apareciendo con los gastos que dejó en este (RN-18): se une
        // por el responsable, no por el hogar actual del usuario.
        List<OwnerAmount> byOwner = jdbc.query("""
                        select u.id, u.name, sum(e.amount) as total
                        from expenses e
                            join users u on u.id = e.owner_id
                        where %s
                        group by u.id, u.name
                        order by total desc, u.name""".formatted(where.sql()),
                where.params(),
                (rs, row) -> new OwnerAmount(
                        rs.getObject("id", UUID.class), rs.getString("name"), money(rs.getBigDecimal("total"))));

        List<PaymentMethodAmount> byPaymentMethod = jdbc.query("""
                        select e.payment_method, sum(e.amount) as total
                        from expenses e
                        where %s
                        group by e.payment_method
                        order by total desc, e.payment_method""".formatted(where.sql()),
                where.params(),
                (rs, row) -> new PaymentMethodAmount(
                        PaymentMethod.fromDatabaseValue(rs.getString("payment_method")),
                        money(rs.getBigDecimal("total"))));

        BigDecimal total = byPaymentMethod.stream()
                .map(PaymentMethodAmount::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(money(total), byCategory, byOwner, byPaymentMethod);
    }

    /**
     * Serie mensual y media histórica (HU-14). Un punto por cada mes con gasto del período, del más
     * viejo al más nuevo, incluido el mes en curso.
     *
     * <p>La media es lo habitual contra lo que se compara el mes: el promedio de los meses con gasto
     * (RN-14), con los demás filtros pero sin el período, y sin el mes en curso. Con el período, mirar
     * un solo mes daría una media igual a ese mes; con el mes en curso, unos pocos días de gasto
     * contarían como un mes entero y la hundirían. Sin meses anteriores con gasto, la media es cero.
     * Se redondea a centavos con {@link RoundingMode#HALF_EVEN}, el redondeo bancario, que no sesga
     * los promedios hacia arriba.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlySeries monthly(AnalysisFilter filter) {
        List<MonthAmount> months = monthTotals(Where.of(filter));

        Where history = Where.of(filter.withoutPeriod());
        history.params().addValue("currentMonth", YearMonth.from(today()).atDay(1));
        List<MonthAmount> completed = monthTotals(
                new Where(history.sql() + " and e.expense_date < :currentMonth", history.params()));

        if (completed.isEmpty()) {
            return new MonthlySeries(months, money(BigDecimal.ZERO));
        }
        BigDecimal sum = completed.stream().map(MonthAmount::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(completed.size()), MONEY_SCALE, RoundingMode.HALF_EVEN);
        return new MonthlySeries(months, average);
    }

    private List<MonthAmount> monthTotals(Where where) {
        return jdbc.query("""
                        select date_trunc('month', e.expense_date)::date as month, sum(e.amount) as total
                        from expenses e
                        where %s
                        group by 1
                        order by 1""".formatted(where.sql()),
                where.params(),
                (rs, row) -> new MonthAmount(
                        YearMonth.from(rs.getObject("month", LocalDate.class)), money(rs.getBigDecimal("total"))));
    }

    /**
     * Los comercios con mayor gasto acumulado (HU-19), hasta {@code limit}. Sin período, el mes en
     * curso (RD-07).
     *
     * <p>El comercio se tipea a mano o llega de una notificación del banco, así que "Carrefour",
     * "CARREFOUR " y "Carrefour" con un espacio duro al final son el mismo. Se normalizan los espacios
     * antes de agrupar: cualquier tira de espacios, tabulaciones, saltos de línea, espacios duros o de
     * ancho cero pasa a ser un espacio, y se recortan los extremos. {@code trim()} de Postgres solo
     * recorta el espacio ASCII. Se agrupa sin distinguir mayúsculas y se muestra la forma que más se
     * repite. A igual total, el orden es alfabético.
     */
    @Transactional(readOnly = true)
    public List<MerchantAmount> topMerchants(AnalysisFilter filter, int limit) {
        Where where = Where.of(withCurrentMonthByDefault(filter));
        MapSqlParameterSource params = where.params().addValue("limit", limit);
        return jdbc.query("""
                        with normalized as (
                            select btrim(regexp_replace(e.merchant, '[\\s\\u00A0\\u2007\\u202F\\u200B\\uFEFF]+', ' ', 'g'))
                                       as merchant,
                                   e.amount
                            from expenses e
                            where %s)
                        select mode() within group (order by merchant) as merchant,
                               sum(amount) as total,
                               count(*) as expenses
                        from normalized
                        group by lower(merchant)
                        order by total desc, merchant
                        limit :limit""".formatted(where.sql()),
                params,
                (rs, row) -> new MerchantAmount(
                        rs.getString("merchant"), money(rs.getBigDecimal("total")), rs.getInt("expenses")));
    }

    private AnalysisFilter withCurrentMonthByDefault(AnalysisFilter filter) {
        YearMonth current = YearMonth.from(today());
        return filter.withDefaultPeriod(current.atDay(1), current.atEndOfMonth());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // Las sumas de numeric(12, 2) ya vienen con dos decimales; solo el cero inicial viene sin ellos.
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * La condición común a todas las consultas. El texto SQL es fijo y solo depende de qué filtros
     * vienen; los valores van siempre como parámetros, nunca concatenados.
     */
    private record Where(String sql, MapSqlParameterSource params) {

        static Where of(AnalysisFilter filter) {
            StringBuilder sql = new StringBuilder("e.active");
            MapSqlParameterSource params = new MapSqlParameterSource();
            if (filter.from() != null) {
                sql.append(" and e.expense_date >= :from");
                params.addValue("from", filter.from());
            }
            if (filter.to() != null) {
                sql.append(" and e.expense_date <= :to");
                params.addValue("to", filter.to());
            }
            if (filter.uncategorized()) {
                sql.append(" and e.category_id is null");
            } else if (filter.categoryId() != null) {
                sql.append(" and e.category_id = :categoryId");
                params.addValue("categoryId", filter.categoryId());
            }
            if (filter.ownerId() != null) {
                sql.append(" and e.owner_id = :ownerId");
                params.addValue("ownerId", filter.ownerId());
            }
            if (filter.paymentMethod() != null) {
                sql.append(" and e.payment_method = :paymentMethod");
                params.addValue("paymentMethod", filter.paymentMethod().databaseValue());
            }
            return new Where(sql.toString(), params);
        }
    }
}
