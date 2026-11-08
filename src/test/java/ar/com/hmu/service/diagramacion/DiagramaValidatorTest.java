package ar.com.hmu.service.diagramacion;

import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.JornadaLaboral;
import ar.com.hmu.model.TipoJornada;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link DiagramaValidator}: reglas estructurales (horario
 * faltante/invertido, superposición, descanso mínimo de 10 h) y
 * advertencias de carga horaria mensual.
 */
class DiagramaValidatorTest {

    private final UUID empleadoId = UUID.randomUUID();

    private JornadaLaboral jornada(LocalDate fecha, TipoJornada tipo,
                                   LocalDateTime ingreso, LocalDateTime egreso) {
        JornadaLaboral j = new JornadaLaboral();
        j.setId(UUID.randomUUID());
        j.setEmpleadoId(empleadoId);
        j.setFecha(fecha);
        j.setTipo(tipo);
        j.setFechaIngreso(ingreso);
        j.setFechaEgreso(egreso);
        return j;
    }

    private JornadaLaboral turno(LocalDate fecha, int horaIngreso, int horasDuracion) {
        LocalDateTime ingreso = fecha.atTime(horaIngreso, 0);
        return jornada(fecha, TipoJornada.TURNO_NORMAL, ingreso, ingreso.plusHours(horasDuracion));
    }

    // ============================================================
    // Reglas estructurales
    // ============================================================

    @Test
    void validar_listaVacia_sinViolaciones() {
        assertThat(DiagramaValidator.validar(List.of())).isEmpty();
        assertThat(DiagramaValidator.validar(null)).isEmpty();
    }

    @Test
    void validar_turnoSinHorario_esViolacion() {
        JornadaLaboral sinHorario = jornada(LocalDate.of(2026, 7, 6),
                TipoJornada.TURNO_NORMAL, null, null);

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(sinHorario));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("sin horario");
        assertThat(violaciones.get(0).empleadoId()).isEqualTo(empleadoId);
    }

    @Test
    void validar_francoSinHorario_esValido() {
        JornadaLaboral franco = jornada(LocalDate.of(2026, 7, 6), TipoJornada.FRANCO, null, null);

        assertThat(DiagramaValidator.validar(List.of(franco))).isEmpty();
    }

    @Test
    void validar_egresoNoPosterioralIngreso_esViolacion() {
        LocalDateTime ingreso = LocalDateTime.of(2026, 7, 6, 14, 0);
        JornadaLaboral invertida = jornada(LocalDate.of(2026, 7, 6),
                TipoJornada.TURNO_NORMAL, ingreso, ingreso.minusHours(7));

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(invertida));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("egreso debe ser posterior");
    }

    @Test
    void validar_jornadasSuperpuestas_esViolacion() {
        // 07:00-14:00 y 12:00-19:00 el mismo día: se pisan 2 horas.
        JornadaLaboral maniana = turno(LocalDate.of(2026, 7, 6), 7, 7);
        JornadaLaboral tarde = turno(LocalDate.of(2026, 7, 6), 12, 7);

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(maniana, tarde));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("Superposición");
    }

    @Test
    void validar_descansoMenorA10Horas_esViolacion() {
        // Egreso lunes 22:00, ingreso martes 07:00: 9 h de descanso.
        JornadaLaboral lunes = turno(LocalDate.of(2026, 7, 6), 15, 7);   // 15-22
        JornadaLaboral martes = turno(LocalDate.of(2026, 7, 7), 7, 7);   // 07-14

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(lunes, martes));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("Descanso insuficiente");
        assertThat(violaciones.get(0).fecha()).isEqualTo(LocalDate.of(2026, 7, 7));
    }

    @Test
    void validar_descansoDeJusto10Horas_esValido() {
        // Egreso lunes 21:00, ingreso martes 07:00: exactamente 10 h.
        JornadaLaboral lunes = turno(LocalDate.of(2026, 7, 6), 14, 7);   // 14-21
        JornadaLaboral martes = turno(LocalDate.of(2026, 7, 7), 7, 7);   // 07-14

        assertThat(DiagramaValidator.validar(List.of(lunes, martes))).isEmpty();
    }

    @Test
    void validar_nocturnasConsecutivasQueCruzanMedianoche_sonValidas() {
        // 21:00-07:00 del día siguiente, dos noches seguidas: 14 h de descanso.
        JornadaLaboral noche1 = turno(LocalDate.of(2026, 7, 6), 21, 10);
        JornadaLaboral noche2 = turno(LocalDate.of(2026, 7, 7), 21, 10);

        assertThat(DiagramaValidator.validar(List.of(noche1, noche2))).isEmpty();
    }

    @Test
    void validar_violacionesDeEmpleadosDistintos_noSeMezclan() {
        // Empleado A con jornada válida; empleado B con superposición.
        JornadaLaboral deA = turno(LocalDate.of(2026, 7, 6), 7, 7);

        UUID otroEmpleado = UUID.randomUUID();
        JornadaLaboral deB1 = turno(LocalDate.of(2026, 7, 6), 7, 7);
        deB1.setEmpleadoId(otroEmpleado);
        JornadaLaboral deB2 = turno(LocalDate.of(2026, 7, 6), 10, 7);
        deB2.setEmpleadoId(otroEmpleado);

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(deA, deB1, deB2));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).empleadoId()).isEqualTo(otroEmpleado);
    }

    // ============================================================
    // Límites de duración por tipo (definición del usuario 2026-07-08)
    // ============================================================

    @Test
    void validar_turnoDeMenosDeUnaHora_esViolacion() {
        // 30 minutos: por debajo del mínimo de 1 h.
        LocalDateTime ingreso = LocalDateTime.of(2026, 7, 6, 7, 0);
        JornadaLaboral corta = jornada(LocalDate.of(2026, 7, 6),
                TipoJornada.TURNO_NORMAL, ingreso, ingreso.plusMinutes(30));

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(corta));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("al menos 1 h");
    }

    @Test
    void validar_turnoDeUnaHora_esValido() {
        assertThat(DiagramaValidator.validar(List.of(
                turno(LocalDate.of(2026, 7, 6), 7, 1)))).isEmpty();
    }

    @Test
    void validar_turnoDeMasDe12Horas_esViolacion() {
        List<Violacion> violaciones = DiagramaValidator.validar(List.of(
                turno(LocalDate.of(2026, 7, 6), 7, 13)));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("máximo de 12 h");
    }

    @Test
    void validar_guardiaDe24Horas_esValida() {
        LocalDateTime ingreso = LocalDateTime.of(2026, 7, 6, 8, 0);
        JornadaLaboral guardia24 = jornada(LocalDate.of(2026, 7, 6),
                TipoJornada.GUARDIA_ACTIVA, ingreso, ingreso.plusHours(24));

        assertThat(DiagramaValidator.validar(List.of(guardia24))).isEmpty();
    }

    @Test
    void validar_guardiaDeMasDe24Horas_esViolacion() {
        // La extensión del egreso real pertenece a marcaciones, no al diagrama.
        LocalDateTime ingreso = LocalDateTime.of(2026, 7, 6, 8, 0);
        JornadaLaboral guardia25 = jornada(LocalDate.of(2026, 7, 6),
                TipoJornada.GUARDIA_ACTIVA, ingreso, ingreso.plusHours(25));

        List<Violacion> violaciones = DiagramaValidator.validar(List.of(guardia25));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.get(0).mensaje()).contains("máximo de 24 h");
    }

    // ============================================================
    // Advertencias de carga mensual
    // ============================================================

    @Test
    void advertencias_nocturnoConCargaDistintaALaEsperada_advierte() {
        HorarioNocturno nocturno = new HorarioNocturno();
        nocturno.setDuracionJornadaHoras(10);
        nocturno.setNumeroJornadasMensuales(14); // espera 140 h

        // Sólo 2 noches planificadas: 20 h.
        List<JornadaLaboral> jornadas = List.of(
                turno(LocalDate.of(2026, 7, 6), 21, 10),
                turno(LocalDate.of(2026, 7, 8), 21, 10));

        List<Violacion> advertencias = DiagramaValidator.advertenciasCargaMensual(
                jornadas, Map.of(empleadoId, nocturno));

        assertThat(advertencias).hasSize(1);
        assertThat(advertencias.get(0).mensaje()).contains("20 h").contains("140 h");
    }

    @Test
    void advertencias_ferianteBajoElMinimoMensual_advierte() {
        HorarioFeriante feriante = new HorarioFeriante();
        feriante.setHorasMinimasMensuales(120);

        List<JornadaLaboral> jornadas = List.of(turno(LocalDate.of(2026, 7, 11), 8, 12)); // 12 h

        List<Violacion> advertencias = DiagramaValidator.advertenciasCargaMensual(
                jornadas, Map.of(empleadoId, feriante));

        assertThat(advertencias).hasSize(1);
        assertThat(advertencias.get(0).mensaje()).contains("mínimo 120 h");
    }

    @Test
    void advertencias_guardiaEnfermeriaBajoLoEsperado_advierte() {
        HorarioGuardiaEnfermeria enfermeria = new HorarioGuardiaEnfermeria();
        enfermeria.setNumeroGuardias12Horas(10);
        enfermeria.setDuracionGuardia12Horas(12);
        enfermeria.setNumeroGuardias10Horas(2);
        enfermeria.setDuracionGuardia10Horas(10); // espera 140 h

        List<JornadaLaboral> jornadas = List.of(turno(LocalDate.of(2026, 7, 8), 7, 12)); // 12 h

        List<Violacion> advertencias = DiagramaValidator.advertenciasCargaMensual(
                jornadas, Map.of(empleadoId, enfermeria));

        assertThat(advertencias).hasSize(1);
        assertThat(advertencias.get(0).mensaje()).contains("140 h");
    }

    @Test
    void advertencias_modalidadSemanal_seSalteaEnEstePase() {
        HorarioEstandar estandar = new HorarioEstandar(); // modalidad semanal: diferida
        estandar.setHorasPorDia(7);

        List<JornadaLaboral> jornadas = List.of(turno(LocalDate.of(2026, 7, 6), 7, 7));

        List<Violacion> advertencias = DiagramaValidator.advertenciasCargaMensual(
                jornadas, Map.of(empleadoId, (HorarioBase) estandar));

        assertThat(advertencias).isEmpty();
    }

    @Test
    void advertencias_cargaCumplida_sinAdvertencias() {
        HorarioNocturno nocturno = new HorarioNocturno();
        nocturno.setDuracionJornadaHoras(10);
        nocturno.setNumeroJornadasMensuales(2); // espera 20 h

        List<JornadaLaboral> jornadas = List.of(
                turno(LocalDate.of(2026, 7, 6), 21, 10),
                turno(LocalDate.of(2026, 7, 8), 21, 10)); // 20 h exactas

        assertThat(DiagramaValidator.advertenciasCargaMensual(
                jornadas, Map.of(empleadoId, nocturno))).isEmpty();
    }
}
