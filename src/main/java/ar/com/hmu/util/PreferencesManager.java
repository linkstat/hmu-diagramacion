package ar.com.hmu.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

public class PreferencesManager {
    private Properties properties;
    private Path prefsFilePath;

    /**
     * Constructor por defecto para preferencias generales de la aplicación.
     */
    public PreferencesManager() {
        this.prefsFilePath = Paths.get(getDefaultPrefsFilePath());
        properties = new Properties();
        loadProperties();
    }

    /**
     * Constructor que acepta un identificador único para preferencias específicas de un usuario.
     *
     * @param userId Identificador único del usuario (por ejemplo, CUIL).
     */
    public PreferencesManager(String userId) {
        this.prefsFilePath = Paths.get(getUserPrefsFilePath(userId));
        properties = new Properties();
        loadProperties();
    }

    /**
     * Obtiene la ruta predeterminada para las preferencias generales.
     *
     * @return Ruta como String.
     */
    private String getDefaultPrefsFilePath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String baseDir;

        if (osName.contains("win")) {
            baseDir = System.getenv("LocalAppData") + File.separator + AppInfo.CFG_FOLDER_NAME;
        } else {
            baseDir = System.getProperty("user.home") + File.separator + ".config" + File.separator + AppInfo.CFG_FOLDER_NAME;
        }

        return baseDir + File.separator + "preferences.properties";
    }

    /**
     * Obtiene la ruta para las preferencias específicas de un usuario.
     *
     * @param userId Identificador único del usuario.
     * @return Ruta como String.
     */
    private String getUserPrefsFilePath(String userId) {
        String osName = System.getProperty("os.name").toLowerCase();
        String baseDir;

        if (osName.contains("win")) {
            baseDir = System.getenv("LocalAppData") + File.separator + AppInfo.CFG_FOLDER_NAME + File.separator + userId;
        } else {
            baseDir = System.getProperty("user.home") + File.separator + ".config" + File.separator + AppInfo.CFG_FOLDER_NAME + File.separator + userId;
        }

        return baseDir + File.separator + "window.properties";
    }

    /**
     * Carga las propiedades desde el archivo especificado.
     */
    private void loadProperties() {
        try {
            if (Files.exists(prefsFilePath)) {
                try (InputStream input = Files.newInputStream(prefsFilePath)) {
                    properties.load(input);
                }
            } else {
                // Crear directorios y archivo si no existen
                Files.createDirectories(prefsFilePath.getParent());
                Files.createFile(prefsFilePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al cargar las preferencias desde " + prefsFilePath, e);
        }
    }

    /**
     * Guarda las propiedades en el archivo especificado.
     */
    private void saveProperties() {
        try (OutputStream output = Files.newOutputStream(prefsFilePath)) {
            properties.store(output, AppInfo.PRG_SHORT_TITLE + " Preferences");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error al guardar las preferencias en " + prefsFilePath, e);
        }
    }

    /**
     * Almacena un par clave-valor.
     *
     * @param key   Clave de la preferencia.
     * @param value Valor de la preferencia.
     */
    public void put(String key, String value) {
        properties.setProperty(key, value);
        saveProperties();
    }

    /**
     * Recupera el valor asociado a una clave.
     *
     * @param key          Clave de la preferencia.
     * @param defaultValue Valor por defecto si la clave no existe.
     * @return Valor de la preferencia.
     */
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    // Métodos para manejar UUID
    public void putUUID(String key, UUID value) {
        if (value != null) {
            properties.setProperty(key, value.toString());
        } else {
            properties.remove(key);
        }
        saveProperties();
    }

    public UUID getUUID(String key, UUID defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return UUID.fromString(value);
        }
        return defaultValue;
    }

    // Métodos para otros tipos de datos (int, boolean) si es necesario
    public void putInt(String key, Integer value) {
        if (value != null) {
            properties.setProperty(key, value.toString());
        } else {
            properties.remove(key);
        }
        saveProperties();
    }

    public Integer getInt(String key, Integer defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Log o manejar el error según sea necesario
            }
        }
        return defaultValue;
    }

    public void putBoolean(String key, Boolean value) {
        if (value != null) {
            properties.setProperty(key, value.toString());
        } else {
            properties.remove(key);
        }
        saveProperties();
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

}
