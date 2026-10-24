package io.github.patorinaldi.gastos.api.service.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Serie mensual y su media histórica (RF-27, RN-14). */
public record MonthlySeries(List<MonthAmount> months, BigDecimal average) {

    public record MonthAmount(YearMonth month, BigDecimal total) {
    }
}
