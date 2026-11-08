package ar.com.hmu.service.diagramacion;

import ar.com.hmu.constants.TipoUsuario;
import ar.com.hmu.exceptions.ServiceException;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.EstadoDiagrama;
import ar.com.hmu.model.Horario;
import ar.com.hmu.model.HorarioAbierto;
import ar.com.hmu.model.HorarioBase;
import ar.com.hmu.model.HorarioConFranquicia;
import ar.com.hmu.model.HorarioDXI;
import ar.com.hmu.model.HorarioEstandar;
import ar.com.hmu.model.HorarioFeriante;
import ar.com.hmu.model.HorarioGuardiaEnfermeria;
import ar.com.hmu.model.HorarioGuardiaMedica;
import ar.com.hmu.model.HorarioJefeServicioGuardiaPasiva;
import ar.com.hmu.model.HorarioNocturno;
import ar.com.hmu.model.HorarioSemanal;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.DiagramaRepository;
import ar.com.hmu.repository.HorarioRepository;
import ar.com.hmu.util.DiaSemana;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asignar modalidad horaria (CU04): la OP elige la modalidad del catálogo,
 * ve sus parámetros, indica desde cuándo rige y, si corresponde, una
 * franquicia con su período. La modalidad anterior se cierra el día previo
 * y la nueva queda vigente para la generación de la grilla (CU11) y el
 * motor de validación (CU13).
 */
public class ModalidadHorariaService {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Vigencia abierta: la modalidad rige hasta que se asigne otra. */
    static final LocalDateTime SIN_FIN = LocalDateTime.of(2099, 12, 31, 23, 59);
    private static final List<DiaSemana> LUNES_A_VIERNES = List.of(DiaSemana.LUNES, DiaSemana.MARTES,
            DiaSemana.MIERCOLES, DiaSemana.JUEVES, DiaSemana.VIERNES);

    /** Parámetro editable de una modalidad. */
    public record Parametro(String clave, String etiqueta, int valor, int minimo, int maximo) {
    }

    /** Catálogo de modalidades del HMU (E4: modalidades horarias). */
    public enum Modalidad {
        ESTANDAR("Estándar", "35 h semanales, 7 h por día en días hábiles.",
                List.of(new Parametro("horasPorDia", "Horas por día", 7, 1, 12))),
        SEMANAL("Semanal", "35 h semanales distribuidas en forma irregular (por defecto 7 h de lunes a viernes).",
                List.of(new Parametro("horasPorDia", "Horas por día hábil", 7, 1, 12),
                        new Parametro("horaInicio", "Hora de ingreso", 8, 0, 23))),
        NOCTURNO("Nocturno", "140 h mensuales: 14 jornadas de 10 h (típicamente 21:00 a 07:00).",
                List.of(new Parametro("duracion", "Horas por jornada", 10, 6, 12),
                        new Parametro("jornadas", "Jornadas mensuales", 14, 1, 31))),
        FERIANTE("Feriante", "Mínimo 120 h mensuales: 10 guardias de 12 h en días no laborables.",
                List.of(new Parametro("duracion", "Horas por guardia", 12, 6, 24),
                        new Parametro("horasMinimas", "Horas mínimas mensuales", 120, 1, 300))),
        GUARDIA_MEDICA("Guardia médica", "36 h semanales: 3 guardias de 12 h.",
                List.of(new Parametro("duracion", "Horas por guardia", 12, 6, 24),
                        new Parametro("guardiasSemanales", "Guardias por semana", 3, 1, 7),
                        new Parametro("descanso", "Descanso mínimo (h)", 10, 0, 48))),
        GUARDIA_ENFERMERIA("Guardia enfermería", "140 h mensuales: 10 guardias de 12 h y 2 de 10 h.",
                List.of(new Parametro("guardias12", "Guardias de 12 h", 10, 0, 31),
                        new Parametro("guardias10", "Guardias de 10 h", 2, 0, 31))),
        DXI("Diagnóstico por Imágenes", "24 h semanales (legislación especial).",
                List.of(new Parametro("horasSemanales", "Horas semanales", 24, 1, 48),
                        new Parametro("horaInicio", "Hora de ingreso", 8, 0, 23))),
        JEFE_GUARDIA_PASIVA("Jefe de servicio con guardia pasiva", "Una hora menos por día (6 h en días hábiles).",
                List.of(new Parametro("horasPorDia", "Horas por día", 6, 1, 12))),
        ABIERTO("Abierto", "35 h semanales en cualquier horario.",
                List.of(new Parametro("horasSemanales", "Horas semanales", 35, 1, 48)));

        private final String nombre;
        private final String descripcion;
        private final List<Parametro> parametros;

        Modalidad(String nombre, String descripcion, List<Parametro> parametros) {
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.parametros = parametros;
        }

        public String descripcion() {
            return descripcion;
        }

        public List<Parametro> parametros() {
            return parametros;
        }

        @Override
        public String toString() {
            return nombre;
        }

        public static Modalidad de(HorarioBase h) {
            HorarioBase base = h instanceof HorarioConFranquicia f ? f.getHorarioDecorado() : h;
            return switch (base) {
                case HorarioEstandar x -> ESTANDAR;
                case HorarioSemanal x -> SEMANAL;
                case HorarioNocturno x -> NOCTURNO;
                case HorarioFeriante x -> FERIANTE;
                case HorarioGuardiaMedica x -> GUARDIA_MEDICA;
                case HorarioGuardiaEnfermeria x -> GUARDIA_ENFERMERIA;
                case HorarioDXI x -> DXI;
                case HorarioJefeServicioGuardiaPasiva x -> JEFE_GUARDIA_PASIVA;
                case HorarioAbierto x -> ABIERTO;
                default -> null;
            };
        }
    }

    private final HorarioRepository horarioRepository;
    private final DiagramaRepository diagramaRepository;

    public ModalidadHorariaService(HorarioRepository horarioRepository, DiagramaRepository diagramaRepository) {
        this.horarioRepository = horarioRepository;
        this.diagramaRepository = diagramaRepository;
    }

    public HorarioBase vigente(Usuario agente) throws ServiceException {
        try {
            return horarioRepository.findHorarioActualDeEmpleado(agente.getId());
        } catch (SQLException e) {
            throw new ServiceException("Error al leer la modalidad del agente", e);
        }
    }

    /** "Guardia médica · 12 h por guardia, 3 por semana · desde 01/01/2026". */
    public static String describir(HorarioBase h) {
        if (h == null) return "Sin modalidad asignada";
        Modalidad m = Modalidad.de(h);
        Horario base = (Horario) (h instanceof HorarioConFranquicia f ? f.getHorarioDecorado() : h);
        StringBuilder sb = new StringBuilder(m != null ? m.toString() : base.getClass().getSimpleName());
        Map<String, Integer> p = parametrosDe(base);
        if (m != null && !p.isEmpty()) {
            sb.append(" · ");
            List<String> partes = new ArrayList<>();
            for (Parametro def : m.parametros()) {
                Integer v = p.get(def.clave());
                if (v != null) partes.add(def.etiqueta().toLowerCase() + ": " + v);
            }
            sb.append(String.join(", ", partes));
        }
        if (base.getFechaIngreso() != null) {
            sb.append(" · desde el ").append(base.getFechaIngreso().toLocalDate().format(DDMMYYYY));
        }
        if (h instanceof HorarioConFranquicia f) {
            sb.append(" · franquicia de ").append(f.getHorasFranquicia()).append(" h hasta el ")
                    .append(f.getFechaEgreso().toLocalDate().format(DDMMYYYY));
        }
        return sb.toString();
    }

    static Map<String, Integer> parametrosDe(Horario h) {
        Map<String, Integer> p = new LinkedHashMap<>();
        switch (h) {
            case HorarioEstandar x -> p.put("horasPorDia", x.getHorasPorDia());
            case HorarioJefeServicioGuardiaPasiva x -> p.put("horasPorDia", x.getHorasPorDia());
            case HorarioNocturno x -> {
                p.put("duracion", x.getDuracionJornadaHoras());
                p.put("jornadas", x.getNumeroJornadasMensuales());
            }
            case HorarioFeriante x -> {
                p.put("duracion", x.getDuracionGuardiaHoras());
                p.put("horasMinimas", x.getHorasMinimasMensuales());
            }
            case HorarioGuardiaMedica x -> {
                p.put("duracion", x.getDuracionGuardiaHoras());
                p.put("guardiasSemanales", x.getNumeroGuardiasSemanal());
                p.put("descanso", x.getTiempoDescansoMinimoHoras());
            }
            case HorarioGuardiaEnfermeria x -> {
                p.put("guardias12", x.getNumeroGuardias12Horas());
                p.put("guardias10", x.getNumeroGuardias10Horas());
            }
            case HorarioDXI x -> p.put("horasSemanales", x.getHorasSemanales());
            case HorarioAbierto x -> p.put("horasSemanales", x.getHorasSemanales());
            default -> { }
        }
        return p;
    }

    /**
     * CU04 3a: la fecha cae dentro de un diagrama aprobado del servicio del
     * agente; el cambio rige desde el período siguiente o requiere novedad.
     */
    public Optional<String> advertencia(Usuario agente, LocalDate desde) {
        if (agente == null || desde == null || agente.getServicioId() == null) return Optional.empty();
        try {
            for (DiagramaDeServicio d : diagramaRepository.findByServicio(agente.getServicioId())) {
                if (d.getEstado() == EstadoDiagrama.APROBADO && !desde.isBefore(d.getFechaInicio())
                        && !desde.isAfter(d.getFechaFin())) {
                    return Optional.of("El " + desde.format(DDMMYYYY) + " está dentro del diagrama aprobado del "
                            + d.getFechaInicio().format(DDMMYYYY) + " al " + d.getFechaFin().format(DDMMYYYY)
                            + ": ese diagrama no cambia; la modalidad se aplica desde el período siguiente "
                            + "(los cambios dentro del período requieren una novedad).");
                }
            }
        } catch (SQLException ignored) {
            // Sin aviso.
        }
        return Optional.empty();
    }

    /** CU04 pasos 3 y 4. @return la modalidad registrada. */
    public HorarioBase asignar(Usuario agente, Modalidad modalidad, Map<String, Integer> valores, LocalDate desde,
                               Integer horasFranquicia, LocalDate franquiciaHasta, Usuario actor)
            throws ServiceException {
        if (actor == null || !actor.hasRole(TipoUsuario.OFICINADEPERSONAL)) {
            throw new ServiceException("Sólo la Oficina de Personal asigna modalidades horarias.");
        }
        if (agente == null || modalidad == null || desde == null) {
            throw new ServiceException("Indique el agente, la modalidad y la fecha desde la que rige.");
        }
        for (Parametro def : modalidad.parametros()) {
            Integer v = valores.get(def.clave());
            if (v == null || v < def.minimo() || v > def.maximo()) {
                throw new ServiceException("«" + def.etiqueta() + "» debe estar entre " + def.minimo()
                        + " y " + def.maximo() + ".");
            }
        }
        if (horasFranquicia != null) {
            if (horasFranquicia < 1 || horasFranquicia > 6) {
                throw new ServiceException("La franquicia debe ser de 1 a 6 horas.");
            }
            if (franquiciaHasta == null || !franquiciaHasta.isAfter(desde)) {
                throw new ServiceException("Indique hasta cuándo rige la franquicia (posterior al inicio).");
            }
        }
        try {
            HorarioBase anterior = horarioRepository.findHorarioActualDeEmpleado(agente.getId());
            Horario nuevo = construir(modalidad, valores);
            nuevo.setFechaIngreso(desde.atStartOfDay());
            nuevo.setFechaEgreso(SIN_FIN);
            HorarioBase asignado = nuevo;
            if (horasFranquicia != null) {
                HorarioConFranquicia f = new HorarioConFranquicia();
                f.setHorarioDecorado(nuevo);
                f.setHorasFranquicia(horasFranquicia);
                f.setFechaIngreso(desde.atStartOfDay());
                f.setFechaEgreso(franquiciaHasta.atTime(23, 59));
                asignado = f;
            }
            horarioRepository.create(asignado);
            if (anterior != null) {
                horarioRepository.cerrarVigencia(anterior, desde.minusDays(1).atTime(23, 59));
            }
            horarioRepository.asignarHorarioActual(agente.getId(), asignado.getId());
            return asignado;
        } catch (SQLException | RuntimeException e) {
            throw new ServiceException("Error al asignar la modalidad: " + e.getMessage(), e);
        }
    }

    private static Horario construir(Modalidad m, Map<String, Integer> v) {
        return switch (m) {
            case ESTANDAR -> {
                HorarioEstandar h = new HorarioEstandar();
                h.setDiasLaborables(new ArrayList<>(LUNES_A_VIERNES));
                h.setHorasPorDia(v.get("horasPorDia"));
                yield h;
            }
            case JEFE_GUARDIA_PASIVA -> {
                HorarioJefeServicioGuardiaPasiva h = new HorarioJefeServicioGuardiaPasiva();
                h.setDiasLaborables(new ArrayList<>(LUNES_A_VIERNES));
                h.setHorasPorDia(v.get("horasPorDia"));
                yield h;
            }
            case SEMANAL -> {
                HorarioSemanal h = new HorarioSemanal();
                Map<DiaSemana, Integer> horas = new EnumMap<>(DiaSemana.class);
                Map<DiaSemana, LocalTime> inicio = new EnumMap<>(DiaSemana.class);
                for (DiaSemana d : LUNES_A_VIERNES) {
                    horas.put(d, v.get("horasPorDia"));
                    inicio.put(d, LocalTime.of(v.get("horaInicio"), 0));
                }
                h.setDistribucionSemanal(horas);
                h.setHoraInicioPorDia(inicio);
                yield h;
            }
            case DXI -> {
                HorarioDXI h = new HorarioDXI();
                Map<DiaSemana, Integer> horas = new EnumMap<>(DiaSemana.class);
                Map<DiaSemana, LocalTime> inicio = new EnumMap<>(DiaSemana.class);
                int porDia = Math.max(1, v.get("horasSemanales") / LUNES_A_VIERNES.size());
                for (DiaSemana d : LUNES_A_VIERNES) {
                    horas.put(d, porDia);
                    inicio.put(d, LocalTime.of(v.get("horaInicio"), 0));
                }
                h.setDistribucionHoraria(horas);
                h.setHoraInicioPorDia(inicio);
                h.setHorasSemanales(v.get("horasSemanales"));
                yield h;
            }
            case NOCTURNO -> {
                HorarioNocturno h = new HorarioNocturno();
                h.setDiasProgramados(new ArrayList<>());
                h.setDuracionJornadaHoras(v.get("duracion"));
                h.setNumeroJornadasMensuales(v.get("jornadas"));
                yield h;
            }
            case FERIANTE -> {
                HorarioFeriante h = new HorarioFeriante();
                h.setDiasNoLaborables(new ArrayList<>());
                h.setGuardiasProgramadas(new ArrayList<>());
                h.setDuracionGuardiaHoras(v.get("duracion"));
                h.setHorasMinimasMensuales(v.get("horasMinimas"));
                yield h;
            }
            case GUARDIA_MEDICA -> {
                HorarioGuardiaMedica h = new HorarioGuardiaMedica();
                h.setFechasGuardias(new ArrayList<>());
                h.setDuracionGuardiaHoras(v.get("duracion"));
                h.setNumeroGuardiasSemanal(v.get("guardiasSemanales"));
                h.setTiempoDescansoMinimoHoras(v.get("descanso"));
                h.setPermitirGuardiasContinuas(false);
                yield h;
            }
            case GUARDIA_ENFERMERIA -> {
                HorarioGuardiaEnfermeria h = new HorarioGuardiaEnfermeria();
                h.setFechasGuardias(new ArrayList<>());
                h.setDuracionGuardia12Horas(12);
                h.setDuracionGuardia10Horas(10);
                h.setNumeroGuardias12Horas(v.get("guardias12"));
                h.setNumeroGuardias10Horas(v.get("guardias10"));
                yield h;
            }
            case ABIERTO -> {
                HorarioAbierto h = new HorarioAbierto();
                h.setHorasSemanales(v.get("horasSemanales"));
                h.setFlexibilidadHoraria(true);
                h.setPreferenciasHorarias(new ArrayList<>());
                yield h;
            }
        };
    }
}
