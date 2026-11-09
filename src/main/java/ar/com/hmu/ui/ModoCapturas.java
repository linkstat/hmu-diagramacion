package ar.com.hmu.ui;

import ar.com.hmu.controller.AvisosDialog;
import ar.com.hmu.controller.BandejaDiagramasController;
import ar.com.hmu.controller.DiagramacionServicioController;
import ar.com.hmu.controller.FeriadosController;
import ar.com.hmu.controller.MainMenuMosaicoController;
import ar.com.hmu.controller.NovedadesController;
import ar.com.hmu.controller.ReglasValidacionController;
import ar.com.hmu.controller.SolicitarNovedadController;
import ar.com.hmu.model.DiagramaDeServicio;
import ar.com.hmu.model.Memorandum;
import ar.com.hmu.model.NovedadAdjunto;
import ar.com.hmu.model.Usuario;
import ar.com.hmu.repository.ContextoAuditoria;
import ar.com.hmu.service.notification.NotificationService;
import ar.com.hmu.service.notification.TipoEventoDiagrama;
import ar.com.hmu.service.notification.TipoEventoMemorandum;
import ar.com.hmu.service.novedades.PerfilNovedades;
import ar.com.hmu.util.AppInfo;
import ar.com.hmu.util.Reloj;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Modo de desarrollo para las capturas del documento del TFG
 * ({@code --capturas} o {@code -Dhmu.capturas}): sin iniciar sesión y
 * sin enviar correos, abre cada pantalla de PANTALLAS.md con los datos del
 * seed ({@code sql/seed_capturas.sql}), la lleva al estado definido y guarda
 * un PNG de 1600×900 con {@code Node.snapshot}. Los diálogos se componen
 * centrados sobre su ventana, como se ven en pantalla. No modifica datos.
 *
 * <p>Opciones (propiedades del sistema): {@code hmu.hoy} (fecha de
 * referencia, por defecto 2026-10-07, a las 08:15), {@code hmu.capturas.salida}
 * (por defecto ~/demo/capturas) y {@code hmu.capturas.solo} (lista de
 * ids separados por coma, p. ej. {@code P03,P12}).</p>
 */
public final class ModoCapturas {

    static final int ANCHO = 1600;
    static final int ALTO = 900;

    // Usuarios del seed (CUIL ficticios).
    static final long CUIL_OP = 27111122277L;      // Medina, Rosa
    static final long CUIL_JS = 27991000018L;      // Paz, Mariela (Guardia Central)
    static final long CUIL_DH = 20111111122L;      // García, Pedro
    static final long CUIL_AGENTE = 27991000050L;  // Moreno, Daniela
    static final long CUIL_OBSERVADA = 27991000115L; // Correa, Agustina

    static final UUID SERVICIO_GC = UUID.fromString("de300000-0000-4000-a000-000000000001");
    static final UUID DIAGRAMA_NOV = UUID.fromString("de400000-0000-4000-d000-000000000011");
    static final UUID DIAGRAMA_OCT = UUID.fromString("de400000-0000-4000-d000-000000000010");
    static final UUID NOV_CG_MORENO = nov(1);
    static final UUID NOV_OM_CORREA = nov(6);
    static final UUID AGENTE_LEDESMA = UUID.fromString("de300000-0000-4000-b000-000000000004");
    static final UUID AGENTE_MORENO = UUID.fromString("de300000-0000-4000-b000-000000000005");
    static final UUID AGENTE_GODOY = UUID.fromString("de300000-0000-4000-b000-000000000006");
    static final UUID AGENTE_FIGUEROA = UUID.fromString("de300000-0000-4000-b000-000000000007");
    static final UUID DIAGRAMA_CIRUGIA = UUID.fromString("de300000-0000-4000-d000-000000000002");

    private static volatile boolean activo;

    private final ContextoAplicacion ctx;
    private final Path salida;
    private final Set<String> solo;
    private final Deque<Paso> pasos = new ArrayDeque<>();
    private final List<String> generadas = new ArrayList<>();
    private final List<String> fallidas = new ArrayList<>();

    private record Paso(String nombre, long esperaMs, Accion accion) {
    }

    @FunctionalInterface
    private interface Accion {
        void run() throws Exception;
    }

    private ModoCapturas(ContextoAplicacion ctx, Path salida, Set<String> solo) {
        this.ctx = ctx;
        this.salida = salida;
        this.solo = solo;
    }

    /** Las pantallas consultan esto para no abrir diálogos bloqueantes (alertas al iniciar sesión). */
    public static boolean activo() {
        return activo;
    }

    static boolean solicitado(List<String> args) {
        return args.contains("--capturas") || System.getProperty("hmu.capturas") != null;
    }

    static void ejecutar(Stage primaryStage, List<String> args) throws IOException {
        activo = true;
        // Entre una captura y otra puede no quedar ninguna ventana abierta.
        Platform.setImplicitExit(false);
        // Hora fija: las capturas muestran siempre las 08:15 del día simulado.
        Reloj.fijar(LocalDate.parse(System.getProperty("hmu.hoy", "2026-10-07")), java.time.LocalTime.of(8, 15));
        Path salida = Path.of(System.getProperty("hmu.capturas.salida",
                System.getProperty("user.home") + "/demo/capturas"));
        Files.createDirectories(salida);
        String filtro = System.getProperty("hmu.capturas.solo", "");
        Set<String> solo = filtro.isBlank() ? Set.of()
                : Arrays.stream(filtro.split(",")).map(String::trim).collect(Collectors.toSet());
        ModoCapturas m = new ModoCapturas(ContextoAplicacion.crear(new CorreoDeshabilitado()), salida, solo);
        m.planificar();
        System.out.println("[capturas] " + m.pasos.size() + " pasos; salida: " + salida);
        m.siguiente();
    }

    // ============================================================
    // Plan de capturas (PANTALLAS.md)
    // ============================================================

    private void planificar() {
        // P06 — Menú principal de la OP.
        captura("P06", () -> {
            Stage s = menuPrincipal(CUIL_OP);
            fijarTamanio(s);
            return s;
        });

        // P05 — Inicio de sesión con la cuenta bloqueada tras 5 intentos (CU01 2b).
        captura("P05", () -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/loginScreen.fxml"));
            Parent root = loader.load();
            ctx.inyectar((ar.com.hmu.controller.LoginController) loader.getController());
            ((ar.com.hmu.controller.LoginController) loader.getController()).postInitialize();
            ((javafx.scene.control.TextField) root.lookup("#usernameField")).setText("27-99100005-0");
            ((javafx.scene.control.PasswordField) root.lookup("#passwordField")).setText("********");
            Stage base = ventana(root, "Inicio de sesión");
            base.setResizable(false);
            base.show();
            javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    "La cuenta quedó bloqueada por 5 intentos fallidos consecutivos.\n"
                            + "Solicite el desbloqueo a la Oficina de Personal.");
            alerta.setTitle("Error");
            alerta.setHeaderText(null);
            alerta.initOwner(base);
            alerta.show();
            return new Stage[]{base, (Stage) alerta.getDialogPane().getScene().getWindow()};
        });

        // P01 — Diagramación de noviembre (borrador) con error bloqueante y advertencias.
        Stage[] p01 = new Stage[1];
        DiagramacionServicioController[] c01 = new DiagramacionServicioController[1];
        paso("P01 abrir", 300, () -> {
            if (!incluida("P01") && !incluida("P01b")) return;
            Usuario js = usuario(CUIL_JS);
            var r = diagramacion(js, false);
            p01[0] = r.stage();
            c01[0] = r.controller();
            c01[0].seleccionarDiagrama(SERVICIO_GC, DIAGRAMA_NOV);
            mostrar(p01[0]);
        });
        paso("P01 validar", 900, () -> {
            if (p01[0] == null) return;
            ((Button) p01[0].getScene().getRoot().lookup("#validarButton")).fire();
        });
        paso("P01 guardar", 1200, () -> {
            if (p01[0] == null || !incluida("P01")) return;
            guardar("P01_diagramacion_mensual", p01[0]);
        });
        // P01b — Editor de jornada: nocturno de Figueroa con cruce de medianoche (21:00 → 07:00 +1).
        Stage[] p01b = new Stage[1];
        paso("P01b abrir", 300, () -> {
            if (p01[0] == null || !incluida("P01b")) return;
            // Modal (showAndWait): el paso siguiente corre dentro de su ciclo de eventos.
            c01[0].editarCelda(AGENTE_FIGUEROA, LocalDate.of(2026, 11, 2));
        });
        paso("P01b guardar", 900, () -> {
            p01b[0] = ventanaTitulada("Editar jornada");
            if (p01b[0] != null) {
                p01b[0].getScene().getRoot().requestFocus();
                guardarConDialogo("P01b_editor_jornada", p01[0], p01b[0]);
                p01b[0].close();
            }
            if (p01[0] != null) p01[0].close();
        });

        // P02 — Consulta de diagramas de servicios (OP), todos los estados.
        Stage[] p02 = new Stage[1];
        paso("P02 abrir", 300, () -> {
            if (!incluida("P02")) return;
            p02[0] = bandejaDiagramas(usuario(CUIL_OP));
            @SuppressWarnings("unchecked")
            ComboBox<Object> estado = (ComboBox<Object>) p02[0].getScene().getRoot().lookup("#estadoFiltroCombo");
            estado.getSelectionModel().select(1);   // "Todos"
            mostrar(p02[0]);
        });
        paso("P02 guardar", 1000, () -> {
            if (p02[0] == null) return;
            guardar("P02_consulta_diagramas", p02[0]);
            p02[0].close();
        });

        // P02b — Observar diagrama (OP): Cirugía, pendiente de aprobación, con el comentario cargado.
        Stage[] p02b = new Stage[2];
        paso("P02b abrir", 300, () -> {
            if (!incluida("P02b")) return;
            var r = diagramacion(usuario(CUIL_OP), false);
            p02b[0] = r.stage();
            UUID cirugia = ctx.servicioService.readAll().stream().filter(sv -> "Cirugía".equals(sv.getNombre()))
                    .findFirst().orElseThrow().getId();
            r.controller().seleccionarDiagrama(cirugia, DIAGRAMA_CIRUGIA);
            mostrar(p02b[0]);
        });
        paso("P02b observar", 900, () -> {
            if (p02b[0] == null) return;
            ((Button) p02b[0].getScene().getRoot().lookup("#observarButton")).fire();
        });
        paso("P02b comentario", 600, () -> {
            p02b[1] = ventanaTitulada("Observar diagrama");
            if (p02b[1] == null) return;
            ((javafx.scene.control.TextArea) p02b[1].getScene().getRoot().lookup(".text-area")).setText(
                    "Faltan las guardias del fin de semana largo del 21 al 23/11; el servicio queda sin "
                            + "cobertura nocturna esos días.");
            p02b[1].getScene().getRoot().requestFocus();
        });
        paso("P02b guardar", 600, () -> {
            if (p02b[1] != null) {
                guardarConDialogo("P02b_observar_diagrama", p02b[0], p02b[1]);
                p02b[1].close();
            }
            if (p02b[0] != null) p02b[0].close();
        });

        // P03 — Novedades de la OP con el CG de Moreno seleccionado (Fig. 23).
        NovedadesController[] c03 = new NovedadesController[1];
        paso("P03 abrir", 300, () -> {
            if (!incluida("P03")) return;
            c03[0] = novedades(usuario(CUIL_OP));
            mostrar(c03[0].getStage());
            c03[0].seleccionar(NOV_CG_MORENO);
        });
        paso("P03 guardar", 1000, () -> {
            if (c03[0] == null) return;
            guardar("P03_novedades_op", c03[0].getStage());
        });

        // P03c — Devolver para corrección: diálogo con el fundamento.
        Stage[] dialogo = new Stage[1];
        paso("P03c abrir", 300, () -> {
            if (c03[0] == null || !incluida("P03c")) return;
            dialogo[0] = null;
            // El diálogo es modal (showAndWait): el paso siguiente corre dentro de su ciclo de eventos.
            NovedadesController.pedirTextoNoBloqueante("Devolver para corrección",
                    "Cambio de guardia de Moreno, Daniela (18/10/2026 al 23/10/2026)\n"
                            + "Indique qué debe corregir el agente:", "Devolver",
                    "La constancia adjunta no indica la fecha del examen; adjunte una constancia con fecha y horario.",
                    c03[0].getStage(), d -> dialogo[0] = d);
        });
        paso("P03c guardar", 900, () -> {
            if (dialogo[0] == null) return;
            guardarConDialogo("P03c_devolver_para_correccion", c03[0].getStage(), dialogo[0]);
            dialogo[0].close();
        });
        // P15 — Licencia de origen externo (CU29): Sosa, superpuesta con su cambio de horario en trámite.
        Stage[] p15 = new Stage[1];
        paso("P15 abrir", 300, () -> {
            if (c03[0] == null || !incluida("P15")) return;
            var lic = c03[0].abrirLicenciaExterna();
            lic.completar(UUID.fromString("de300000-0000-4000-b000-000000000002"), 11,
                    LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15), "VeDi 2026-121905",
                    "Certificado de reposo por 72 h presentado en Medicina Laboral.");
            p15[0] = (Stage) javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof Stage st && st.getTitle() != null && st.getTitle().startsWith("Licencia"))
                    .findFirst().orElse(null);
        });
        paso("P15 guardar", 900, () -> {
            if (p15[0] == null) return;
            guardarConDialogo("P15_licencia_externa", c03[0].getStage(), p15[0]);
            p15[0].close();
        });

        paso("P03 cerrar", 300, () -> {
            if (c03[0] != null) c03[0].getStage().close();
        });

        // P03 (JS) — Bandeja de la jefatura: pendientes de autorizar.
        NovedadesController[] c03js = new NovedadesController[1];
        paso("P03-JS abrir", 300, () -> {
            if (!incluida("P03-JS")) return;
            c03js[0] = novedades(usuario(CUIL_JS));
            c03js[0].usarPerfil(PerfilNovedades.JEFATURA);
            mostrar(c03js[0].getStage());
            c03js[0].seleccionar(nov(4));
        });
        paso("P03-JS guardar", 1000, () -> {
            if (c03js[0] == null) return;
            guardar("P03-JS_novedades_jefatura", c03js[0].getStage());
            c03js[0].getStage().close();
        });

        // P03 (DH) — Instancia final con la Secretaría de Salud pendiente.
        NovedadesController[] c03dh = new NovedadesController[1];
        paso("P03-DH abrir", 300, () -> {
            if (!incluida("P03-DH")) return;
            c03dh[0] = novedades(usuario(CUIL_DH));
            mostrar(c03dh[0].getStage());
            c03dh[0].seleccionar(nov(11));
        });
        paso("P03-DH guardar", 1000, () -> {
            if (c03dh[0] == null) return;
            guardar("P03-DH_novedades_direccion", c03dh[0].getStage());
            c03dh[0].getStage().close();
        });

        // P03b — Solicitar novedad: CG válido y CG con error bloqueante (Moreno).
        NovedadesController[] c03ag = new NovedadesController[1];
        SolicitarNovedadController[] sol = new SolicitarNovedadController[1];
        paso("P03b abrir", 300, () -> {
            if (!incluida("P03b")) return;
            c03ag[0] = novedades(usuario(CUIL_AGENTE));
            mostrar(c03ag[0].getStage());
            sol[0] = c03ag[0].abrirSolicitud(null);
            sol[0].completar("CG", AGENTE_MORENO, LocalDate.of(2026, 10, 29), AGENTE_LEDESMA,
                    LocalDate.of(2026, 10, 30), null, null,
                    "Trámite familiar el 29/10; intercambio acordado con Ledesma.");
            sol[0].agregarAdjunto(new NovedadAdjunto("conformidad_ledesma.pdf", "application/pdf", new byte[2300]));
        });
        paso("P03b guardar ok", 1100, () -> {
            if (sol[0] == null) return;
            guardarConDialogo("P03b_solicitar_novedad_valida", c03ag[0].getStage(), ventanaDe(sol[0]));
            // Variante con error bloqueante: recibir la noche del 15/10 de Godoy deja 1 h de descanso
            // tras su propia guardia de ese día (08:00-20:00).
            sol[0].completar("CG", AGENTE_MORENO, LocalDate.of(2026, 10, 18), AGENTE_GODOY,
                    LocalDate.of(2026, 10, 15), null, null,
                    "Cambio por examen final el 19/10 a la mañana.");
            sol[0].quitarAdjuntosNuevos();
            sol[0].agregarAdjunto(new NovedadAdjunto("conformidad_godoy.pdf", "application/pdf", new byte[2100]));
        });
        paso("P03b guardar error", 1100, () -> {
            if (sol[0] == null) return;
            guardarConDialogo("P03b_solicitar_novedad_error_bloqueante", c03ag[0].getStage(), ventanaDe(sol[0]));
            ventanaDe(sol[0]).close();
        });
        paso("P03-AG guardar", 900, () -> {
            if (c03ag[0] == null) return;
            c03ag[0].seleccionar(NOV_CG_MORENO);
        });
        paso("P03-AG guardar 2", 900, () -> {
            if (c03ag[0] == null) return;
            guardar("P03-AG_novedades_agente", c03ag[0].getStage());
            c03ag[0].getStage().close();
        });

        // P03d — Avisos en la aplicación (agente).
        captura("P03d", () -> {
            Usuario u = usuario(CUIL_AGENTE);
            Stage base = menuPrincipal(CUIL_AGENTE);
            fijarTamanio(base);
            Stage avisos = AvisosDialog.crear(ctx.notificacionService, u, base);
            avisos.setOnHidden(null);   // no marcar como leídos
            avisos.show();
            return new Stage[]{base, avisos};
        });

        // P08 — Modalidad horaria (CU04): Juárez pasa a feriante con franquicia (aviso 3a).
        Stage[] p08 = new Stage[1];
        paso("P08 abrir", 300, () -> {
            if (!incluida("P08")) return;
            var r = menu(CUIL_OP).crearPantallaModalidad();
            p08[0] = r.getKey();
            mostrar(p08[0]);
            r.getValue().proponer(UUID.fromString("de300000-0000-4000-b000-000000000009"),
                    ar.com.hmu.service.diagramacion.ModalidadHorariaService.Modalidad.FERIANTE,
                    LocalDate.of(2026, 10, 15), 1, LocalDate.of(2026, 12, 31));
        });
        paso("P08 guardar", 1000, () -> {
            if (p08[0] == null) return;
            guardar("P08_modalidad_horaria", p08[0]);
            p08[0].close();
        });

        // P04 — Parte diario del 06/10 (Fig. 24): se genera si falta y se registra la carga manual de Moreno.
        Stage[] p04 = new Stage[1];
        ar.com.hmu.controller.ParteDiarioController[] c04 = new ar.com.hmu.controller.ParteDiarioController[1];
        paso("P04 abrir", 300, () -> {
            if (!incluida("P04")) return;
            Usuario op = usuario(CUIL_OP);
            // Partes del 01 al 05/10 generados y cerrados (base de los indicadores, P16).
            for (int dia = 1; dia <= 5; dia++) {
                LocalDate f = LocalDate.of(2026, 10, dia);
                var v = ctx.parteDiarioService.consultar(f);
                if (v.parte() == null) v = ctx.parteDiarioService.generar(f, op);
                if (!v.parte().estaCerrado()) ctx.parteDiarioService.cerrar(v.parte(), op);
            }
            LocalDate fecha = LocalDate.of(2026, 10, 6);
            var vista = ctx.parteDiarioService.consultar(fecha);
            if (vista.parte() == null) {
                vista = ctx.parteDiarioService.generar(fecha, op);
            }
            boolean conCarga = vista.detalles().stream().anyMatch(d -> d.getAgenteId().equals(AGENTE_MORENO)
                    && d.getOrigen() == ar.com.hmu.service.asistencia.DetalleParte.Origen.CARGA_MANUAL);
            if (!conCarga && !vista.parte().estaCerrado()) {
                ctx.parteDiarioService.cargaManual(vista.parte(), AGENTE_MORENO,
                        ar.com.hmu.model.CodigoNovedad.CAPACITACION,
                        "Capacitación en RCP convocada por la Dirección.", op);
            }
            var r = menu(CUIL_OP).crearPantallaParte(fecha);
            p04[0] = r.getKey();
            c04[0] = r.getValue();
            mostrar(p04[0]);
            c04[0].seleccionarAgente(UUID.fromString("de300000-0000-4000-b000-000000000002"));
            c04[0].prepararCarga(UUID.fromString("de300000-0000-4000-b000-000000000002"),
                    ar.com.hmu.model.CodigoNovedad.PRESENTE,
                    "Constancia del jefe de guardia: egreso a las 14:05.");
        });
        paso("P04 guardar", 1200, () -> {
            if (p04[0] == null) return;
            guardar("P04_parte_diario", p04[0]);
        });
        // P04b — Resumen de la importación del archivo del 07/10 (vista previa: no se guarda).
        Stage[] p04b = new Stage[1];
        paso("P04b abrir", 300, () -> {
            if (p04[0] == null || !incluida("P04b")) return;
            Path csv = Path.of("docs/demo/reloj_hmu_20261007.csv");
            if (!Files.exists(csv)) return;
            var resumen = ctx.parteDiarioService.analizar(csv.getFileName().toString(), Files.readAllBytes(csv),
                    usuario(CUIL_OP));
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    ar.com.hmu.controller.ParteDiarioController.resumen(resumen));
            a.setTitle("Importar marcaciones");
            a.setHeaderText("Resumen de la importación");
            a.initOwner(p04[0]);
            a.show();
            p04b[0] = (Stage) a.getDialogPane().getScene().getWindow();
        });
        paso("P04b guardar", 900, () -> {
            if (p04b[0] != null) {
                guardarConDialogo("P04b_importar_marcaciones", p04[0], p04b[0]);
                p04b[0].close();
            }
            if (p04[0] != null) p04[0].close();
        });

        // P16 — Indicadores de octubre (CU36): partes cerrados del 01 al 05/10; el 06 queda abierto.
        Stage[] p16 = new Stage[1];
        paso("P16 abrir", 300, () -> {
            if (!incluida("P16")) return;
            var r = menu(CUIL_DH).crearPantallaIndicadores(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            p16[0] = r.getKey();
            mostrar(p16[0]);
        });
        paso("P16 guardar", 1500, () -> {
            if (p16[0] == null) return;
            guardar("P16_indicadores", p16[0]);
            p16[0].close();
        });

        // P17 — Banco de horas de Moreno (agente): crédito vencido, FC del 22/10 que consumió FIFO y las
        // horas extras del 01/10 (cómputo doble desde las 21:00), que acreditó el cotejo de la captura P04.
        Stage[] p17 = new Stage[1];
        paso("P17 abrir", 300, () -> {
            if (!incluida("P17")) return;
            var r = menu(CUIL_AGENTE).crearPantallaBancoHoras();
            p17[0] = r.getKey();
            mostrar(p17[0]);
        });
        paso("P17 guardar", 1200, () -> {
            if (p17[0] == null) return;
            guardar("P17_banco_horas", p17[0]);
            p17[0].close();
        });

        // P07 — Gestión de agentes (CU03): ficha de un agente.
        Stage[] p07 = new Stage[1];
        paso("P07 abrir", 300, () -> {
            if (!incluida("P07")) return;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmUsuario.fxml"));
            loader.setControllerFactory(cls -> {
                if (cls == ar.com.hmu.controller.AbmUsuarioController.class) {
                    var c = new ar.com.hmu.controller.AbmUsuarioController();
                    c.setServices(ctx.usuarioService, ctx.cargoService, ctx.servicioService, ctx.domicilioService,
                            ctx.roleService);
                    return c;
                }
                return instanciar(cls);
            });
            Parent root = loader.load();
            p07[0] = ventana(root, "Alta, Baja y Modificación de Agentes");
            p07[0].show();
            @SuppressWarnings("unchecked")
            ComboBox<Usuario> combo = (ComboBox<Usuario>) root.lookup("#busquedaComboBox");
            combo.getItems().stream().filter(u -> u.getCuil() == CUIL_AGENTE).findFirst().ifPresent(u -> {
                combo.getSelectionModel().select(u);
                combo.fireEvent(new javafx.event.ActionEvent());
            });
            root.requestFocus();   // sin el cursor en el CUIL
        });
        paso("P07 guardar", 1200, () -> {
            if (p07[0] == null) return;
            guardar("P07_gestion_agentes", p07[0]);
            p07[0].close();
        });

        // P11 — Gestión de servicios (CU07).
        Stage[] p11 = new Stage[1];
        paso("P11 abrir", 300, () -> {
            if (!incluida("P11")) return;
            Usuario op = usuario(CUIL_OP);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/abmServicio.fxml"));
            loader.setControllerFactory(cls -> {
                if (cls == ar.com.hmu.controller.AbmServicioController.class) {
                    var c = new ar.com.hmu.controller.AbmServicioController();
                    c.setServices(ctx.servicioService);
                    c.setUsuarioActual(op);
                    return c;
                }
                return instanciar(cls);
            });
            Parent root = loader.load();
            p11[0] = ventana(root, "Gestión de Servicios");
            p11[0].show();
            @SuppressWarnings("unchecked")
            javafx.scene.control.TableView<ar.com.hmu.model.Servicio> tabla =
                    (javafx.scene.control.TableView<ar.com.hmu.model.Servicio>) root.lookup("#serviciosTableView");
            tabla.getItems().stream().filter(sv -> "Guardia Central".equals(sv.getNombre())).findFirst()
                    .ifPresent(sv -> {
                        tabla.getSelectionModel().select(sv);
                        tabla.scrollTo(sv);
                    });
        });
        paso("P11 guardar", 1200, () -> {
            if (p11[0] == null) return;
            guardar("P11_gestion_servicios", p11[0]);
            p11[0].close();
        });

        // P09 — Importar listado de agentes (CU05): vista previa, sin confirmar.
        Stage[] p09 = new Stage[1];
        paso("P09 abrir", 300, () -> {
            if (!incluida("P09")) return;
            Path csv = Path.of("docs/demo/padron_nuevos_agentes.csv");
            if (!Files.exists(csv)) return;
            var r = menu(CUIL_OP).crearPantallaImportarPadron();
            p09[0] = r.getKey();
            mostrar(p09[0]);
            r.getValue().analizar(csv.getFileName().toString(), Files.readAllBytes(csv));
        });
        paso("P09 guardar", 1000, () -> {
            if (p09[0] == null) return;
            guardar("P09_importar_padron", p09[0]);
            p09[0].close();
        });

        // P10 — Roles con vigencia (CU06): Ledesma, con su jefatura interina de julio finalizada, y la
        // propuesta de una nueva designación interina en Guardia Central.
        Stage[] p10 = new Stage[1];
        paso("P10 abrir", 300, () -> {
            if (!incluida("P10")) return;
            var r = menu(CUIL_OP).crearPantallaRoles();
            p10[0] = r.getKey();
            mostrar(p10[0]);
            r.getValue().proponer(20991000048L, ar.com.hmu.constants.TipoUsuario.JEFATURADESERVICIO, "Guardia Central",
                    LocalDate.of(2026, 10, 19), "Memorándum DH N.º 112/2026");
        });
        paso("P10 guardar", 1000, () -> {
            if (p10[0] == null) return;
            guardar("P10_roles_vigencia", p10[0]);
            p10[0].close();
        });

        // P12 — Reglas de validación (CU08).
        Stage[] p12 = new Stage[1];
        ReglasValidacionController[] c12 = new ReglasValidacionController[1];
        paso("P12 abrir", 300, () -> {
            if (!incluida("P12")) return;
            MainMenuMosaicoController menu = menu(CUIL_OP);
            var r = menu.crearPantallaReglas();
            p12[0] = r.getKey();
            c12[0] = r.getValue();
            mostrar(p12[0]);
            c12[0].seleccionar("RV-NOV-TOPE-FJ");
        });
        paso("P12 proponer", 800, () -> {
            if (c12[0] == null) return;
            c12[0].proponer("topeMensual", "3", LocalDate.of(2026, 10, 15));
        });
        paso("P12 guardar", 800, () -> {
            if (p12[0] == null) return;
            guardar("P12_reglas_validacion", p12[0]);
            p12[0].close();
        });

        // P13 — Feriados.
        Stage[] p13 = new Stage[1];
        paso("P13 abrir", 300, () -> {
            if (!incluida("P13")) return;
            p13[0] = feriados(usuario(CUIL_OP));
            mostrar(p13[0]);
        });
        paso("P13 guardar", 1000, () -> {
            if (p13[0] == null) return;
            guardar("P13_feriados", p13[0]);
            p13[0].close();
        });

        // P14 — Mi Diagrama (agente).
        Stage[] p14 = new Stage[1];
        paso("P14 abrir", 300, () -> {
            if (!incluida("P14")) return;
            var r = diagramacion(usuario(CUIL_AGENTE), true);
            p14[0] = r.stage();
            r.controller().seleccionarDiagrama(SERVICIO_GC, DIAGRAMA_OCT);
            mostrar(p14[0]);
        });
        paso("P14 guardar", 1200, () -> {
            if (p14[0] == null) return;
            guardar("P14_mi_diagrama", p14[0]);
            p14[0].close();
        });
    }

    // ============================================================
    // Pantallas
    // ============================================================

    private record Diagramacion(Stage stage, DiagramacionServicioController controller) {
    }

    private Diagramacion diagramacion(Usuario u, boolean modoEmpleado) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/diagramacionServicio.fxml"));
        loader.setControllerFactory(cls -> {
            if (cls == DiagramacionServicioController.class) {
                DiagramacionServicioController c = new DiagramacionServicioController();
                c.setDiagramaService(ctx.diagramaService);
                c.setServicioService(ctx.servicioService);
                c.setUsuarioActual(u);
                return c;
            }
            return instanciar(cls);
        });
        Parent root = loader.load();
        DiagramacionServicioController c = loader.getController();
        if (modoEmpleado) {
            c.activarModoEmpleado();
        }
        c.postInitialize();
        return new Diagramacion(ventana(root, modoEmpleado ? "Mi Diagrama" : "Diagramación de servicio"), c);
    }

    private Stage bandejaDiagramas(Usuario u) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/bandejaDiagramas.fxml"));
        loader.setControllerFactory(cls -> {
            if (cls == BandejaDiagramasController.class) {
                BandejaDiagramasController c = new BandejaDiagramasController();
                c.setDiagramaService(ctx.diagramaService);
                c.setServicioService(ctx.servicioService);
                c.setUsuarioRepository(ctx.usuarioRepository);
                c.setUsuarioActual(u);
                return c;
            }
            return instanciar(cls);
        });
        Parent root = loader.load();
        ((BandejaDiagramasController) loader.getController()).postInitialize();
        return ventana(root, "Consulta de Diagramas");
    }

    private Stage feriados(Usuario u) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/feriados.fxml"));
        loader.setControllerFactory(cls -> {
            if (cls == FeriadosController.class) {
                FeriadosController c = new FeriadosController();
                c.setFeriadoService(ctx.feriadoService);
                c.setUsuarioActual(u);
                return c;
            }
            return instanciar(cls);
        });
        Parent root = loader.load();
        ((FeriadosController) loader.getController()).postInitialize();
        return ventana(root, "Feriados");
    }

    private NovedadesController novedades(Usuario u) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/novedades.fxml"));
        Parent root = loader.load();
        NovedadesController c = loader.getController();
        c.setNovedadService(ctx.novedadService);
        c.setUsuarioActual(u);
        Stage stage = ventana(root, "Novedades");
        c.setStage(stage);
        c.postInitialize();
        return c;
    }

    private MainMenuMosaicoController menu(long cuil) throws Exception {
        return menuConStage(cuil).controller;
    }

    private Stage menuPrincipal(long cuil) throws Exception {
        return menuConStage(cuil).stage;
    }

    private record Menu(Stage stage, MainMenuMosaicoController controller) {
    }

    private Menu menuConStage(long cuil) throws Exception {
        Usuario u = usuario(cuil);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainMenuMosaico.fxml"));
        Parent root = loader.load();
        MainMenuMosaicoController c = loader.getController();
        ctx.inyectar(c);
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle("Menú Principal :: " + AppInfo.PRG_LONG_TITLE);
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        c.postInitialize(u, stage);
        stage.show();
        return new Menu(stage, c);
    }

    private Usuario usuario(long cuil) throws Exception {
        Usuario u = ctx.usuarioService.findUsuarioByCuil(cuil);
        if (u == null) {
            throw new IllegalStateException("No existe el usuario " + cuil + ": ¿se cargó sql/seed_capturas.sql?");
        }
        ctx.usuarioService.loadAdditionalUserData(u);
        ContextoAuditoria.iniciar(u.getId());
        return u;
    }

    private Stage ventana(Parent root, String titulo) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle(titulo + " :: " + AppInfo.PRG_LONG_TITLE);
        stage.initModality(Modality.NONE);
        stage.getIcons().add(new Image(getClass().getResourceAsStream(AppInfo.ICON_IMAGE)));
        return stage;
    }

    private static Object instanciar(Class<?> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void mostrar(Stage s) {
        fijarTamanio(s);
        s.show();
    }

    private static void fijarTamanio(Stage s) {
        s.setX(0);
        s.setY(0);
        s.setWidth(ANCHO);
        s.setHeight(ALTO);
    }

    private static Stage ventanaTitulada(String prefijo) {
        return (Stage) Window.getWindows().stream()
                .filter(w -> w instanceof Stage st && st.getTitle() != null && st.getTitle().startsWith(prefijo))
                .findFirst().orElse(null);
    }

    private static Stage ventanaDe(SolicitarNovedadController c) {
        return c.getStage();
    }

    // ============================================================
    // Ejecución
    // ============================================================

    private boolean incluida(String id) {
        return solo.isEmpty() || solo.contains(id) || solo.stream().anyMatch(id::startsWith);
    }

    private void paso(String nombre, long esperaMs, Accion accion) {
        pasos.add(new Paso(nombre, esperaMs, accion));
    }

    /** Captura simple: abre la ventana (o ventana + diálogo) y la guarda en el paso siguiente. */
    private void captura(String id, CreadorVentanas creador) {
        Object[] ventanas = new Object[1];
        paso(id + " abrir", 300, () -> {
            if (incluida(id)) ventanas[0] = creador.crear();
        });
        paso(id + " guardar", 1200, () -> {
            if (ventanas[0] == null) return;
            if (ventanas[0] instanceof Stage s) {
                guardar(id + "_" + nombreArchivo(id), s);
                s.close();
            } else if (ventanas[0] instanceof Stage[] par) {
                if (par[0].getWidth() < ANCHO - 50) {
                    guardarCentrado(id + "_" + nombreArchivo(id), par[0], par[1]);
                } else {
                    guardarConDialogo(id + "_" + nombreArchivo(id), par[0], par[1]);
                }
                par[1].close();
                par[0].close();
            }
        });
    }

    @FunctionalInterface
    private interface CreadorVentanas {
        Object crear() throws Exception;
    }

    private static String nombreArchivo(String id) {
        return switch (id) {
            case "P06" -> "menu_principal";
            case "P03d" -> "avisos";
            case "P05" -> "inicio_sesion_cuenta_bloqueada";
            default -> "pantalla";
        };
    }

    private void siguiente() {
        Paso p = pasos.poll();
        if (p == null) {
            // El último paso todavía se está ejecutando: se termina después.
            PauseTransition fin = new PauseTransition(Duration.millis(2000));
            fin.setOnFinished(e -> Platform.runLater(this::terminar));
            fin.play();
            return;
        }
        PauseTransition t = new PauseTransition(Duration.millis(p.esperaMs()));
        // runLater: fuera del pulso de animación (ahí no se permite showAndWait).
        t.setOnFinished(e -> Platform.runLater(() -> {
            // Se agenda el siguiente ANTES de ejecutar: si la acción abre un diálogo
            // modal, la cadena sigue dentro de su ciclo de eventos.
            siguiente();
            try {
                p.accion().run();
            } catch (Exception ex) {
                fallidas.add(p.nombre() + ": " + ex);
                System.err.println("[capturas] falló " + p.nombre() + ": " + ex);
                ex.printStackTrace();
            }
        }));
        t.play();
    }

    private void terminar() {
        System.out.println("[capturas] generadas (" + generadas.size() + "):");
        generadas.forEach(g -> System.out.println("  " + g));
        if (!fallidas.isEmpty()) {
            System.out.println("[capturas] con errores (" + fallidas.size() + "):");
            fallidas.forEach(f -> System.out.println("  " + f));
        }
        Platform.exit();
        System.exit(fallidas.isEmpty() ? 0 : 1);
    }

    // ============================================================
    // PNG
    // ============================================================

    private void guardar(String nombre, Window w) throws IOException {
        WritableImage img = w.getScene().snapshot(null);
        escribir(nombre, img);
    }

    /** La ventana base con el diálogo centrado encima y el fondo atenuado, como se ve en pantalla. */
    private void guardarConDialogo(String nombre, Window base, Window dialogo) throws IOException {
        WritableImage fondo = base.getScene().snapshot(null);
        WritableImage frente = dialogo.getScene().snapshot(null);
        int w = (int) fondo.getWidth();
        int h = (int) fondo.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader pf = fondo.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = pf.getArgb(x, y);
                int r = (int) (((argb >> 16) & 0xff) * 0.62);
                int g = (int) (((argb >> 8) & 0xff) * 0.62);
                int b = (int) ((argb & 0xff) * 0.62);
                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        int fw = (int) frente.getWidth();
        int fh = (int) frente.getHeight();
        int ox = Math.max(0, (w - fw) / 2);
        int oy = Math.max(0, (h - fh) / 2);
        PixelReader pd = frente.getPixelReader();
        for (int y = -1; y <= fh; y++) {
            for (int x = -1; x <= fw; x++) {
                int tx = ox + x;
                int ty = oy + y;
                if (tx < 0 || ty < 0 || tx >= w || ty >= h) continue;
                boolean borde = x < 0 || y < 0 || x == fw || y == fh;
                out.setRGB(tx, ty, borde ? 0x7f8c8d : pd.getArgb(x, y) & 0xffffff);
            }
        }
        escribirBuffered(nombre, out);
    }

    /** Ventana chica (inicio de sesión) centrada sobre un fondo neutro, con su diálogo encima. */
    private void guardarCentrado(String nombre, Window ventana, Window dialogo) throws IOException {
        BufferedImage out = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setColor(new java.awt.Color(0x5d6d7e));
        g.fillRect(0, 0, ANCHO, ALTO);
        g.dispose();
        WritableImage v = ventana.getScene().snapshot(null);
        WritableImage d = dialogo != null ? dialogo.getScene().snapshot(null) : null;
        int altoTotal = (int) v.getHeight() + (d != null ? (int) d.getHeight() + 24 : 0);
        int vx = (ANCHO - (int) v.getWidth()) / 2;
        int vy = (ALTO - altoTotal) / 2;
        pegar(out, v, vx, vy, 1.0);
        if (d != null) {
            for (int y = 0; y < ALTO; y++) {
                for (int x = 0; x < ANCHO; x++) {
                    int rgb = out.getRGB(x, y);
                    int r = (int) (((rgb >> 16) & 0xff) * 0.62);
                    int gg = (int) (((rgb >> 8) & 0xff) * 0.62);
                    int b = (int) ((rgb & 0xff) * 0.62);
                    out.setRGB(x, y, (r << 16) | (gg << 8) | b);
                }
            }
            // Debajo de la ventana, para que se vean los datos ingresados.
            pegar(out, d, (ANCHO - (int) d.getWidth()) / 2, vy + (int) v.getHeight() + 24, 1.0);
        }
        escribirBuffered(nombre, out);
    }

    private static void pegar(BufferedImage out, WritableImage img, int ox, int oy, double factor) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        PixelReader pr = img.getPixelReader();
        for (int y = -1; y <= h; y++) {
            for (int x = -1; x <= w; x++) {
                int tx = ox + x;
                int ty = oy + y;
                if (tx < 0 || ty < 0 || tx >= out.getWidth() || ty >= out.getHeight()) continue;
                boolean borde = x < 0 || y < 0 || x == w || y == h;
                out.setRGB(tx, ty, borde ? 0x7f8c8d : pr.getArgb(x, y) & 0xffffff);
            }
        }
    }

    private void escribir(String nombre, WritableImage img) throws IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, pr.getArgb(x, y) & 0xffffff);
            }
        }
        escribirBuffered(nombre, out);
    }

    private void escribirBuffered(String nombre, BufferedImage img) throws IOException {
        File f = salida.resolve(nombre + ".png").toFile();
        ImageIO.write(img, "png", f);
        generadas.add(f.getName() + " (" + img.getWidth() + "×" + img.getHeight() + ")");
        System.out.println("[capturas] " + f);
    }

    // ============================================================

    private static UUID nov(int n) {
        return UUID.fromString(String.format("de400000-0000-4000-a100-%012d", n));
    }

    /** Canal de correo que no envía nada: el modo de capturas no debe escribir a nadie. */
    private static final class CorreoDeshabilitado implements NotificationService {
        @Override
        public void notify(Usuario destinatario, TipoEventoMemorandum evento, Memorandum memo, String comentarios) {
        }

        @Override
        public void notify(Usuario destinatario, TipoEventoDiagrama evento, DiagramaDeServicio diagrama,
                           String nombreServicio, String comentarios) {
        }

        @Override
        public void notify(Usuario destinatario, String asunto, String cuerpo, Runnable alEnviar) {
        }
    }
}
