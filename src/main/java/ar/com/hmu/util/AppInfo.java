package ar.com.hmu.util;

public class AppInfo {

    public static final String CFG_FOLDER_NAME = "Aromito";
    public static final String PRG_LONG_TITLE = "Sistema de Gestión Hospitalario HMU" ;
    public static final String PRG_SHORT_TITLE = "Aromito" ;
    public static final String NOMBRE_TFG = "HMU Diagramación";
    public static final String VERSION = "1.0.0";
    public static final String BUILD = "110123";
    public static final String PRODUCTION_DATE = "noviembre 2026";
    public static final String LOGO_IMAGE = "/images/mosaic_default_image.png";
    public static final String ICON_IMAGE = "/images/app-icon.png";
    /**
     * Perfil de menú del TFG (por defecto): oculta los módulos fuera del alcance
     * del E4 y los heredados que ahora son tipos de novedad.
     * {@code -Daromito.menu=completo} los vuelve a mostrar.
     */
    public static boolean perfilTfg() {
        return !"completo".equalsIgnoreCase(System.getProperty("aromito.menu", "tfg"));
    }

    /** Nombre que muestra "Acerca de" según el perfil de menú. */
    public static String nombreAcercaDe() {
        return perfilTfg() ? NOMBRE_TFG : PRG_SHORT_TITLE;
    }

    public static final String PRODUCT_INFO = "<html>"
            + "<head>"
            + "    <script>"
            + "        document.addEventListener('contextmenu', function(e) { e.preventDefault(); });"
            + "    </script>"
            + "</head>"
            + "<body style=\"background-color:#F0F0F0;\">"
            + "<p><strong>Autor:</strong> Pablo Alejandro Hamann</p>"
            + "</body>"
            + "</html>";

    public static final String LICENSES_OF_USE = "<html>"
            + "<head>"
            + "    <script>"
            + "        document.addEventListener('contextmenu', function(e) { e.preventDefault(); });"
            + "    </script>"
            + "</head>"
            + "<body style=\"background-color:#F8F8F8;\">"
            + "<h3><span style=\"color: #333333;\">Atribuci&oacute;n de im&aacute;genes:</span></h3>"
            + "<p>"
            + "    <span style=\"color: #333333;\">"
            + "        Este software, en su versi&oacute;n actual y versiones anteriores, incluye im&aacute;genes que requieren atribuci&oacute;n de acuerdo con sus respectivas licencias. Las fuentes de estas im&aacute;genes son las siguientes:"
            + "    </span>"
            + "</p>"
            + "<ul>"
            + "    <li><a title=\"absence icons\" href=\"https://www.flaticon.com/free-icons/absence\">Absence icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"calendar icons\" href=\"https://www.flaticon.com/free-icons/calendar\">Calendar icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"clock icons\" href=\"https://www.flaticon.com/free-icons/clock\">Clock icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"contact list icons\" href=\"https://www.flaticon.com/free-icons/contact-list\">Contact list icons created by Uniconlabs - Flaticon</a></li>"
            + "    <li><a title=\"discipline icons\" href=\"https://www.flaticon.com/free-icons/discipline\">Discipline icons created by Uniconlabs - Flaticon</a></li>"
            + "    <li><a title=\"employment icons\" href=\"https://www.flaticon.com/free-icons/employment\">Employment icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"list icons\" href=\"https://www.flaticon.com/free-icons/list\">List icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"list icons\" href=\"https://www.flaticon.com/free-icons/list\">List icons created by Uniconlabs - Flaticon</a></li>"
            + "    <li><a title=\"family tree icons\" href=\"https://www.flaticon.com/free-icons/family-tree\">Family tree icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"files and folders icons\" href=\"https://www.flaticon.com/free-icons/files-and-folders\">Files and folders icons created by zero_wing - Flaticon</a></li>"
            + "    <li><a title=\"missed icons\" href=\"https://www.flaticon.com/free-icons/missed\">Missed icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"No icons\" href=\"https://www.flaticon.com/free-icons/no\">No icons created by ekays.dsgn - Flaticon</a></li>"
            + "    <li><a title=\"organization icons\" href=\"https://www.flaticon.com/free-icons/organization\">Organization icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"overtime icons\" href=\"https://www.flaticon.com/free-icons/overtime\">Overtime icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"part time icons\" href=\"https://www.flaticon.com/free-icons/part-time\">Part time icons created by gravisio - Flaticon</a></li>"
            + "    <li><a title=\"personal information icons\" href=\"https://www.flaticon.com/free-icons/personal-information\">Personal information icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"recruitment icons\" href=\"https://www.flaticon.com/free-icons/recruitment\">Recruitment icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"seo report icons\" href=\"https://www.flaticon.com/free-icons/seo-report\">Seo report icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"software engineer icons\" href=\"https://www.flaticon.com/free-icons/software-engineer\">Software engineer icons created by Dewi Sari - Flaticon</a></li>"
            + "    <li><a title=\"spreadsheet icons\" href=\"https://www.flaticon.com/free-icons/spreadsheet\">Spreadsheet icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"taking icons\" href=\"https://www.flaticon.com/free-icons/taking\">Taking icons created by Chanut-is-Industries - Flaticon</a></li>"
            + "    <li><a title=\"time sheet icons\" href=\"https://www.flaticon.com/free-icons/time-sheet\">Time sheet icons created by zafdesign - Flaticon</a></li>"
            + "    <li><a title=\"timetable icons\" href=\"https://www.flaticon.com/free-icons/timetable\">Timetable icons created by Freepik - Flaticon</a></li>"
            + "    <li><a title=\"timetable icons\" href=\"https://www.flaticon.com/free-icons/timetable\">Timetable icons created by monkik - Flaticon</a></li>"
            + "    <li><a title=\"user interface icons\" href=\"https://www.flaticon.com/free-icons/user-interface\">User interface icons created by Elzicon - Flaticon</a></li>"
            + "</ul>"
            + "<p>&nbsp;</p>"
            + "<hr />"
            + "<h3><span style=\"color: #333333;\">Otras atribuciones:</span></h3>"
            + "<p><span style=\"color: #333333;\">Las siguientes herramientas se utilizaron para la creaci&oacute;n y asistencia en el modelado de contenido HTML:</span></p>"
            + "<ul>"
            + "    <li><a href=\"https://html5-editor.net/\">HTML Editor - Online Web Content Composer</a></li>"
            + "    <li><a href=\"https://webformatter.com/html\">HTML Formatter</a></li>"
            + "    <li><a href=\"https://www.w3schools.com/html/\">W3Schools HTML Tutorial</a></li>"
            + "    <li><a href=\"https://openjfx.io/javadoc/23/javafx.graphics/javafx/scene/paint/Color.html\">Color (JavaFX 23)</a></li>"
            + "</ul>"
            + "<p>&nbsp;</p>"
            + "</body>"
            + "</html>";
}
