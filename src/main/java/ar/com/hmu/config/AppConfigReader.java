package ar.com.hmu.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clase que se encarga de leer la configuración general de la aplicación desde un archivo YAML.
 * <p>
 * La clase `AppConfigReader` utiliza la biblioteca SnakeYAML para deserializar un archivo de configuración
 * YAML (`config.yaml`) y cargar los valores necesarios para la configuración de la aplicación.
 * Por defecto lee {@code config/config.yaml} del classpath; con {@code -Dhmu.config=<ruta>}
 * lee ese archivo.
 * Proporciona un objeto de tipo {@link AppConfig} que contiene tanto los detalles de conexión a la BD
 * como cualquier otra configuración general de la aplicación (que se agregue en el futuro).
 * <p>
 * Esta clase permite centralizar el acceso a las configuraciones, facilitando la lectura y utilización de parámetros
 * clave que necesitan ser accesibles en diferentes partes de la aplicación.
 */
public class AppConfigReader {

    private AppConfig appConfig;
    private SmtpConfig smtpConfig;


    /**
     * Constructor que inicializa el objeto `AppConfigReader` y carga la configuración de la aplicación.
     * <p>
     * Este constructor lee un archivo YAML (`config.yaml`) que contiene la configuración general de la aplicación,
     * incluyendo detalles de la base de datos y otros parámetros relevantes. Utiliza `LoaderOptions` para personalizar
     * el comportamiento de deserialización del archivo YAML, lo cual ayuda a asegurar la seguridad y control del proceso.
     * El resultado es un objeto de tipo {@link AppConfig} que almacena toda la configuración.
     * <p>
     * @throws RuntimeException si el archivo de configuración no se encuentra o si ocurre un error durante la carga.
     */
    public AppConfigReader() {
        // Crear LoaderOptions para configurar el comportamiento de carga de YAML
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setMaxAliasesForCollections(50); // Configuración opcional para limitar alias (por seguridad)
        loaderOptions.setAllowRecursiveKeys(false); // Opcional, evita llaves recursivas

        // Crear un Constructor que utilice los LoaderOptions
        Constructor constructor = new Constructor(AppConfigSettings.class, loaderOptions);

        // Inicializar Yaml con el constructor
        Yaml yaml = new Yaml(constructor);
        String filePath = "config/config.yaml";

        // -Dhmu.config=<ruta> lee un archivo externo en lugar del config.yaml
        // empaquetado (útil al ejecutar el jar sin recompilar).
        String externo = System.getProperty("hmu.config");
        if (externo != null && !externo.isBlank()) {
            filePath = externo;
        }

        try (InputStream inputStream = externo != null && !externo.isBlank()
                ? Files.newInputStream(Path.of(externo))
                : getClass().getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Archivo " + filePath + " no encontrado");
            }
            AppConfigSettings settings = yaml.load(inputStream);
            this.appConfig = settings.getDb();
            // smtp: NULL si la sección no está; igual lo seteamos como no-op
            // por defecto para que el resto del código no haga null-checks.
            this.smtpConfig = settings.getSmtp() != null ? settings.getSmtp() : new SmtpConfig();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al leer el archivo YAML", e);
        }
    }

    /**
     * Obtiene la configuración de la aplicación.
     *
     * @return un objeto {@link AppConfig} con la configuración general de la aplicación.
     */
    public AppConfig getAppConfig() {
        return appConfig;
    }

    /**
     * Obtiene la configuración SMTP. Nunca devuelve {@code null}: si la
     * sección {@code smtp} no está en el YAML, se devuelve un
     * {@link SmtpConfig} con valores por defecto (host vacío → no-op).
     */
    public SmtpConfig getSmtpConfig() {
        return smtpConfig;
    }

}
