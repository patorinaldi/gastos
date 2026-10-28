package io.github.patorinaldi.gastos.api.service.category;

import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El motor de categorización (RF-20, RF-21, RN-12, RN-17) y la forma de los patrones, sin base.
 */
class RuleSetTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    private static final UUID SUPERMERCADO = UUID.randomUUID();
    private static final UUID TRANSPORTE = UUID.randomUUID();
    private static final UUID RESTAURANTES = UUID.randomUUID();
    private static final UUID SALUD = UUID.randomUUID();

    // ── Patrones ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Almacén de Pepe          | almacen de pepe",
            "  BURGER    King  \t     | burger king",
            "Panadería Ñandú          | panaderia nandu",
            "Ｃａｒｒｅｆｏｕｒ        | carrefour",
    })
    void elPatronSeGuardaEnMinusculaSinTildesYConUnSoloEspacio(String raw, String expected) {
        assertThat(Patterns.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void laClaveSoloTieneLetrasYNumeros() {
        assertThat(Patterns.matchKey("McDonald's Av. 9 de Julio")).isEqualTo("mcdonaldsav9dejulio");
        assertThat(Patterns.matchKey("Super Mercado Rosario")).isEqualTo("supermercadorosario");
        assertThat(Patterns.matchKey("--- ...")).isEmpty();
    }

    // ── Coincidencias ───────────────────────────────────────────────────────

    // HU-08: alcanza con que el patrón esté contenido en el comercio, sin importar mayúsculas, tildes,
    // espacios ni puntuación.
    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(delimiter = '|', value = {
            "Carrefour",
            "CARREFOUR EXPRESS 123",
            "Mercado Pago*Carrefour",
            "Carre four",
    })
    void unPatronCoincideConLasVariantesDelComercio(String merchant) {
        RuleSet rules = RuleSet.of(List.of(rule("carrefour", SUPERMERCADO, T0)));

        assertThat(rules.categoryFor(merchant)).contains(SUPERMERCADO);
    }

    @Test
    void lasTildesNoImportanNiEnElPatronNiEnElComercio() {
        RuleSet rules = RuleSet.of(List.of(rule("farmacia", SALUD, T0)));

        assertThat(rules.categoryFor("FARMÁCIA DEL PUEBLO")).contains(SALUD);
    }

    @Test
    void laPuntuacionDelComercioNoImpideLaCoincidencia() {
        RuleSet rules = RuleSet.of(List.of(rule("mcdonald", RESTAURANTES, T0), rule("pedidosya", RESTAURANTES, T0)));

        assertThat(rules.categoryFor("McDonald's")).contains(RESTAURANTES);
        assertThat(rules.categoryFor("PEDIDOS YA *PROPINA")).contains(RESTAURANTES);
    }

    // RF-21, RN-12: sin coincidencia no hay categoría por defecto.
    @Test
    void sinCoincidenciaNoHayCategoria() {
        RuleSet rules = RuleSet.of(List.of(rule("carrefour", SUPERMERCADO, T0)));

        assertThat(rules.categoryFor("Almacén de Pepe")).isEmpty();
        assertThat(RuleSet.of(List.of()).categoryFor("Carrefour")).isEmpty();
    }

    // ── Desempate (RN-17) ───────────────────────────────────────────────────

    @Test
    void ganaElPatronMasLargo() {
        UUID generico = UUID.randomUUID();
        RuleSet rules = RuleSet.of(List.of(
                rule("super", generico, T0),
                rule("supermercado", SUPERMERCADO, T0.plusSeconds(60)),
                rule("uber", TRANSPORTE, T0),
                rule("uber eats", RESTAURANTES, T0.plusSeconds(60))));

        assertThat(rules.categoryFor("Super Mercado Rosario")).contains(SUPERMERCADO);
        assertThat(rules.categoryFor("UBER EATS")).contains(RESTAURANTES);
        assertThat(rules.categoryFor("Uber Trip")).contains(TRANSPORTE);
    }

    // A igual longitud, la más antigua: agregar una regla nueva no cambia cómo se resolvía un comercio.
    @Test
    void aIgualLongitudGanaLaMasAntigua() {
        RuleSet rules = RuleSet.of(List.of(
                rule("kiosco", SUPERMERCADO, T0.plusSeconds(60)),
                rule("tienda", TRANSPORTE, T0)));

        assertThat(rules.categoryFor("Tienda Kiosco")).contains(TRANSPORTE);
    }

    // Las reglas de la V3 se siembran en la misma transacción y tienen la misma fecha: el desempate no
    // puede depender del orden en que llegan.
    @Test
    void aIgualFechaElResultadoNoDependeDelOrden() {
        CategoryRule a = rule("coto", SUPERMERCADO, T0);
        CategoryRule b = rule("cota", TRANSPORTE, T0);

        assertThat(RuleSet.of(List.of(a, b)).categoryFor("coto cota"))
                .isEqualTo(RuleSet.of(List.of(b, a)).categoryFor("coto cota"));
    }

    // Una regla sin letras ni números estaría contenida en cualquier comercio.
    @Test
    void unaReglaSinLetrasNiNumerosNoCoincideConNada() {
        RuleSet rules = RuleSet.of(List.of(rule("...", SUPERMERCADO, T0)));

        assertThat(rules.categoryFor("Carrefour")).isEmpty();
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    // La entidad no expone id ni fecha de alta: los pone la base. Acá se fijan a mano.
    private static CategoryRule rule(String pattern, UUID category, Instant createdAt) {
        CategoryRule rule = new CategoryRule(UUID.randomUUID(), pattern, category);
        set(rule, "id", UUID.randomUUID());
        set(rule, "createdAt", createdAt);
        return rule;
    }

    private static void set(CategoryRule rule, String field, Object value) {
        try {
            Field f = CategoryRule.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(rule, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
