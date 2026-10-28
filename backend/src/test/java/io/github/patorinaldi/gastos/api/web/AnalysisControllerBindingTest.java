package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.service.analysis.AnalysisFilter;
import io.github.patorinaldi.gastos.api.service.analysis.AnalysisService;
import io.github.patorinaldi.gastos.api.service.analysis.MerchantAmount;
import io.github.patorinaldi.gastos.api.service.analysis.MonthlySeries;
import io.github.patorinaldi.gastos.api.service.analysis.MonthlySeries.MonthAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.CategoryAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.OwnerAmount;
import io.github.patorinaldi.gastos.api.service.analysis.Summary.PaymentMethodAmount;
import io.github.patorinaldi.gastos.api.web.dto.PaymentMethodValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cómo llegan los filtros desde la URL hasta el servicio, y cómo vuelven los totales, sin base. Lo
 * que calculan las consultas lo cubre {@code AnalysisTest}.
 *
 * <p>El conversor del medio de pago es el mismo que registra {@code ApiConvertersConfig}.
 */
class AnalysisControllerBindingTest {

    private final AnalysisService analysis = mock(AnalysisService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void armar() {
        FormattingConversionService conversion = new org.springframework.format.support.DefaultFormattingConversionService();
        conversion.addConverter(String.class, PaymentMethodValue.class, PaymentMethodValue::from);
        // Lo mismo que spring.mvc.format.date: iso, que el armado standalone no lee.
        org.springframework.format.datetime.standard.DateTimeFormatterRegistrar iso =
                new org.springframework.format.datetime.standard.DateTimeFormatterRegistrar();
        iso.setUseIsoFormat(true);
        iso.registerFormatters(conversion);
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(analysis))
                .setConversionService(conversion)
                .build();
        when(analysis.summary(any())).thenReturn(new Summary(new BigDecimal("0.00"), List.of(), List.of(), List.of()));
        when(analysis.monthly(any())).thenReturn(new MonthlySeries(List.of(), new BigDecimal("0.00")));
        when(analysis.topMerchants(any(), anyInt())).thenReturn(List.of());
    }

    // RF-29: los mismos filtros que el listado, con las fechas en ISO y el medio de pago como lo
    // devuelve la API.
    @Test
    void losFiltrosLleganAlServicio() throws Exception {
        UUID categoria = UUID.randomUUID();
        UUID integrante = UUID.randomUUID();

        mockMvc.perform(get("/api/expenses/summary")
                        .param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("categoryId", categoria.toString())
                        .param("ownerId", integrante.toString())
                        .param("paymentMethod", "Tarjeta"))
                .andExpect(status().isOk());

        assertThat(summaryFilter()).isEqualTo(new AnalysisFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), categoria, false, integrante, PaymentMethod.TARJETA));
    }

    // El mes en curso por defecto (RD-07) lo decide el servicio, que tiene el reloj.
    @Test
    void sinFiltrosElServicioRecibeUnFiltroVacio() throws Exception {
        mockMvc.perform(get("/api/expenses/summary")).andExpect(status().isOk());

        assertThat(summaryFilter()).isEqualTo(AnalysisFilter.none());
    }

    @Test
    void laBandejaSeFiltraComoEnElListado() throws Exception {
        mockMvc.perform(get("/api/expenses/summary").param("categoryId", "uncategorized")).andExpect(status().isOk());

        assertThat(summaryFilter().uncategorized()).isTrue();
        assertThat(summaryFilter().categoryId()).isNull();
    }

    @Test
    void unPeriodoInvertidoSeRechaza() throws Exception {
        mockMvc.perform(get("/api/expenses/summary").param("from", "2026-09-30").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(analysis);
    }

    @Test
    void filtrosMalFormadosSeRechazan() throws Exception {
        mockMvc.perform(get("/api/expenses/summary").param("categoryId", "foo")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/expenses/summary").param("paymentMethod", "Cheque")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/expenses/summary").param("from", "01/09/2026")).andExpect(status().isBadRequest());

        verifyNoInteractions(analysis);
    }

    @Test
    void elRankingDeComerciosTieneTope() throws Exception {
        mockMvc.perform(get("/api/expenses/merchants")).andExpect(status().isOk());
        verify(analysis).topMerchants(any(), org.mockito.ArgumentMatchers.eq(10));

        mockMvc.perform(get("/api/expenses/merchants").param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/expenses/merchants").param("limit", "51")).andExpect(status().isBadRequest());
    }

    // Los totales viajan como cadena decimal, el mes como "2026-09", y la fila sin categoría con nulos.
    @Test
    void losTotalesVuelvenComoCadenaDecimal() throws Exception {
        UUID ana = UUID.randomUUID();
        when(analysis.summary(any())).thenReturn(new Summary(
                new BigDecimal("1500.50"),
                List.of(new CategoryAmount(null, null, new BigDecimal("1500.50"))),
                List.of(new OwnerAmount(ana, "Ana", new BigDecimal("1500.50"))),
                List.of(new PaymentMethodAmount(PaymentMethod.TARJETA, new BigDecimal("1500.50")))));
        when(analysis.monthly(any())).thenReturn(new MonthlySeries(
                List.of(new MonthAmount(YearMonth.of(2026, 9), new BigDecimal("1500.50"))), new BigDecimal("1500.50")));
        when(analysis.topMerchants(any(), anyInt()))
                .thenReturn(List.of(new MerchantAmount("Carrefour", new BigDecimal("1500.50"), 3)));

        mockMvc.perform(get("/api/expenses/summary"))
                .andExpect(jsonPath("$.total").value("1500.50"))
                .andExpect(jsonPath("$.byCategory[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$.byCategory[0].total").value("1500.50"))
                .andExpect(jsonPath("$.byOwner[0].name").value("Ana"))
                .andExpect(jsonPath("$.byPaymentMethod[0].paymentMethod").value("Tarjeta"));
        mockMvc.perform(get("/api/expenses/monthly"))
                .andExpect(jsonPath("$.months[0].month").value("2026-09"))
                .andExpect(jsonPath("$.average").value("1500.50"));
        mockMvc.perform(get("/api/expenses/merchants"))
                .andExpect(jsonPath("$[0].merchant").value("Carrefour"))
                .andExpect(jsonPath("$[0].total").value("1500.50"))
                .andExpect(jsonPath("$[0].expenses").value(3));
    }

    // El mapeo entre los dos enumerados es explícito: cada medio de pago llega y vuelve con su etiqueta.
    @Test
    void cadaMedioDePagoSeTraduceEnLosDosSentidos() throws Exception {
        for (PaymentMethod method : PaymentMethod.values()) {
            when(analysis.summary(any())).thenReturn(new Summary(new BigDecimal("1.00"), List.of(), List.of(),
                    List.of(new PaymentMethodAmount(method, new BigDecimal("1.00")))));

            mockMvc.perform(get("/api/expenses/summary").param("paymentMethod", method.databaseValue()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.byPaymentMethod[0].paymentMethod").value(method.databaseValue()));

            assertThat(summaryFilter().paymentMethod()).isEqualTo(method);
        }
    }

    private AnalysisFilter summaryFilter() {
        ArgumentCaptor<AnalysisFilter> filter = ArgumentCaptor.forClass(AnalysisFilter.class);
        verify(analysis, org.mockito.Mockito.atLeastOnce()).summary(filter.capture());
        return filter.getValue();
    }
}
