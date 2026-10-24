package io.github.patorinaldi.gastos.api.service.analysis;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.service.analysis.MonthlySeries.MonthAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.CategoryAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.OwnerAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.PaymentMethodAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
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
 */
@Service
public class AnalysisService {

    private static final int MONEY_SCALE = 2;

    private final NamedParameterJdbcTemplate jdbc;

    public AnalysisService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Total del período y su desglose por categoría, integrante y medio de pago (HU-13). */
    @Transactional(readOnly = true)
    public Summary summary(AnalysisFilter filter) {
        Where where = Where.of(filter);

        BigDecimal total = jdbc.queryForObject(
                "select coalesce(sum(e.amount), 0) from expenses e where " + where.sql(),
                where.params(), BigDecimal.class);

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

        return new Summary(money(total), byCategory, byOwner, byPaymentMethod);
    }

    /**
     * Serie mensual y media histórica (HU-14). Un punto por cada mes con gasto, del más viejo al más
     * nuevo. La media es el promedio de esos meses (RN-14): los meses sin gasto no cuentan como cero.
     * Se redondea a centavos con {@link RoundingMode#HALF_EVEN}, el redondeo bancario, que no sesga
     * los promedios hacia arriba.
     */
    @Transactional(readOnly = true)
    public MonthlySeries monthly(AnalysisFilter filter) {
        Where where = Where.of(filter);
        List<MonthAmount> months = jdbc.query("""
                        select date_trunc('month', e.expense_date)::date as month, sum(e.amount) as total
                        from expenses e
                        where %s
                        group by 1
                        order by 1""".formatted(where.sql()),
                where.params(),
                (rs, row) -> new MonthAmount(
                        YearMonth.from(rs.getObject("month", LocalDate.class)), money(rs.getBigDecimal("total"))));

        if (months.isEmpty()) {
            return new MonthlySeries(months, money(BigDecimal.ZERO));
        }
        BigDecimal sum = months.stream().map(MonthAmount::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(months.size()), MONEY_SCALE, RoundingMode.HALF_EVEN);
        return new MonthlySeries(months, average);
    }

    /**
     * Los comercios con mayor gasto acumulado (HU-19), hasta {@code limit}.
     *
     * <p>El comercio se tipea a mano o llega de una notificación del banco, así que "Carrefour" y
     * "CARREFOUR " son el mismo: se agrupa sin distinguir mayúsculas ni espacios en los extremos, y se
     * muestra la forma que más se repite. A igual total, el orden es alfabético.
     */
    @Transactional(readOnly = true)
    public List<MerchantAmount> topMerchants(AnalysisFilter filter, int limit) {
        Where where = Where.of(filter);
        MapSqlParameterSource params = where.params().addValue("limit", limit);
        return jdbc.query("""
                        select mode() within group (order by trim(e.merchant)) as merchant,
                               sum(e.amount) as total,
                               count(*) as expenses
                        from expenses e
                        where %s
                        group by lower(trim(e.merchant))
                        order by total desc, merchant
                        limit :limit""".formatted(where.sql()),
                params,
                (rs, row) -> new MerchantAmount(
                        rs.getString("merchant"), money(rs.getBigDecimal("total")), rs.getInt("expenses")));
    }

    // Las sumas de numeric(12, 2) ya vienen con dos decimales; solo el cero de coalesce viene sin ellos.
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
