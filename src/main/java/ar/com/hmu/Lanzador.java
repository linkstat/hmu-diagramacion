package ar.com.hmu;

import ar.com.hmu.ui.LoginScreen;

/**
 * Punto de entrada del jar ejecutable. No extiende {@code Application}: así
 * JavaFX arranca desde el classpath sin exigir el module path.
 */
public class Lanzador {

    public static void main(String[] args) {
        LoginScreen.main(args);
    }
}
