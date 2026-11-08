package ar.com.hmu.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class StringUtils {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");

    /**
     * Normaliza una cadena eliminando acentos y convirtiendo a minúsculas.
     *
     * @param texto La cadena a normalizar.
     * @return La cadena normalizada.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        // Normaliza en forma NFD
        String textoNormalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        // Elimina los diacríticos
        textoNormalizado = DIACRITICS_PATTERN.matcher(textoNormalizado).replaceAll("");
        // Convierte a minúsculas
        textoNormalizado = textoNormalizado.toLowerCase();
        return textoNormalizado;
    }

}
