package ar.com.hmu.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Render Markdown → HTML para el cuerpo de los memorándums.
 *
 * <p>Configurado con CommonMark base (sin extensiones por ahora). El HTML
 * resultante se inyecta envuelto en una hoja de estilos mínima para
 * legibilidad dentro del JavaFX WebView del detalle.</p>
 */
public final class MarkdownRenderer {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    private MarkdownRenderer() {
    }

    /** Devuelve un documento HTML completo (con &lt;style&gt; embebido) listo
     *  para cargar en un WebView. */
    public static String renderToHtmlDocument(String markdown) {
        String body = renderToHtmlFragment(markdown);
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: -apple-system, 'Segoe UI', Roboto, sans-serif; " +
                "       font-size: 13px; color: #222; padding: 12px; line-height: 1.5; }" +
                "h1, h2, h3 { color: #1a3a52; margin-top: 1em; }" +
                "code { background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }" +
                "pre { background: #f4f4f4; padding: 8px; border-radius: 4px; overflow-x: auto; }" +
                "blockquote { border-left: 3px solid #ccc; padding-left: 10px; color: #555; }" +
                "</style></head><body>" + body + "</body></html>";
    }

    /** Devuelve sólo el HTML del contenido (sin envoltorio). Útil para tests
     *  o composición. */
    public static String renderToHtmlFragment(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }
}
