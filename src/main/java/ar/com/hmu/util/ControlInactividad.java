package ar.com.hmu.util;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Cierre de sesión por inactividad (RNF01, CU01): cualquier evento de
 * teclado o mouse en cualquier ventana de la aplicación cuenta como
 * actividad; al cumplirse el plazo (30 minutos; {@code
 * -Daromito.inactividad.minutos} lo cambia para demostraciones) se cierran
 * las ventanas secundarias y se vuelve al inicio de sesión.
 */
public final class ControlInactividad {

    private static final EventHandler<InputEvent> ACTIVIDAD = e -> registrarActividad();
    private static final ListChangeListener<Window> NUEVAS_VENTANAS = c -> {
        while (c.next()) {
            c.getAddedSubList().forEach(ControlInactividad::escuchar);
        }
    };

    private static volatile long ultimaActividad = System.currentTimeMillis();
    private static Timeline reloj;

    private ControlInactividad() {
    }

    public static long minutosLimite() {
        return Long.getLong("aromito.inactividad.minutos", 30L);
    }

    public static void registrarActividad() {
        ultimaActividad = System.currentTimeMillis();
    }

    /** Empieza a vigilar la sesión; {@code alVencer} cierra la sesión (en el hilo de JavaFX). */
    public static void iniciar(Stage principal, Runnable alVencer) {
        detener();
        registrarActividad();
        Window.getWindows().forEach(ControlInactividad::escuchar);
        Window.getWindows().addListener(NUEVAS_VENTANAS);
        long limiteMs = minutosLimite() * 60_000L;
        reloj = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            if (System.currentTimeMillis() - ultimaActividad >= limiteMs) {
                detener();
                // Fuera del pulso de animación: el cierre puede mostrar diálogos.
                Platform.runLater(() -> {
                    List<Window> abiertas = new ArrayList<>(Window.getWindows());
                    for (Window w : abiertas) {
                        if (w != principal && w instanceof Stage s) {
                            s.close();
                        }
                    }
                    alVencer.run();
                });
            }
        }));
        reloj.setCycleCount(Timeline.INDEFINITE);
        reloj.play();
    }

    public static void detener() {
        if (reloj != null) {
            reloj.stop();
            reloj = null;
        }
        Window.getWindows().removeListener(NUEVAS_VENTANAS);
    }

    private static void escuchar(Window w) {
        Scene s = w.getScene();
        if (s != null) {
            s.removeEventFilter(InputEvent.ANY, ACTIVIDAD);
            s.addEventFilter(InputEvent.ANY, ACTIVIDAD);
        }
        w.sceneProperty().addListener((o, vieja, nueva) -> {
            if (nueva != null) {
                nueva.removeEventFilter(InputEvent.ANY, ACTIVIDAD);
                nueva.addEventFilter(InputEvent.ANY, ACTIVIDAD);
            }
        });
    }
}
