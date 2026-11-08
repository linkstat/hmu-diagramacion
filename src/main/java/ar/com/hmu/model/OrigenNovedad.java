package ar.com.hmu.model;

/** Origen de una novedad (E4, Tabla 52). Mapea al ENUM PG {@code origen_novedad}. */
public enum OrigenNovedad {
    /** La pide el agente (CU20). */
    SOLICITUD,
    /** Surge de una diferencia del parte diario (CU35). */
    COTEJO,
    /** Se asienta desde los sistemas municipales, sin circuito (CU29). */
    LICENCIA_EXTERNA
}
