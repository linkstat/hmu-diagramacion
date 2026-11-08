package ar.com.hmu.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Movimiento del banco de horas (E4, Tabla 54). Los créditos (horas extras
 * para FC) vencen a los N días (regla RV-BANCO-VENC) y guardan las horas que
 * les quedan; los débitos (francos compensatorios) consumen los créditos por
 * orden de vencimiento (FIFO) y, como los vencimientos, apuntan al crédito
 * que afectan: un FC que consume varios créditos genera un débito por
 * crédito, todos con el mismo novedadId. Los ajustes manuales no apuntan a
 * ningún crédito.
 *
 * @param horas           positivas en los créditos, negativas en débitos y vencimientos.
 * @param saldoResultante saldo vigente del agente después del movimiento.
 */
public record MovimientoHoras(UUID id, UUID agenteId, UUID novedadId, LocalDate fecha, Tipo tipo, BigDecimal horas,
                              String concepto, BigDecimal saldoResultante, LocalDate fechaVencimiento,
                              BigDecimal horasRestantes, UUID creditoId) {

    public enum Tipo {
        CREDITO("Crédito"), DEBITO("Débito"), VENCIMIENTO("Vencimiento"), AJUSTE("Ajuste");

        private final String etiqueta;

        Tipo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Crédito con horas sin usar a la fecha (no vencido). */
    public boolean vigenteEl(LocalDate fecha) {
        return tipo == Tipo.CREDITO && horasRestantes != null && horasRestantes.signum() > 0
                && (fechaVencimiento == null || !fechaVencimiento.isBefore(fecha));
    }
}
