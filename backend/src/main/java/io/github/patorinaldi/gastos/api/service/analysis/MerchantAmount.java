package io.github.patorinaldi.gastos.api.service.analysis;

import java.math.BigDecimal;

/** Un comercio del ranking (RF-28): su total acumulado y cuántos gastos lo forman. */
public record MerchantAmount(String merchant, BigDecimal total, int expenses) {
}
