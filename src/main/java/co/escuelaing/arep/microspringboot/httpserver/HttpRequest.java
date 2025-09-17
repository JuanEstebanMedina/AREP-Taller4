package co.escuelaing.arep.microspringboot.httpserver;

import java.net.URI;

/**
 *
 * @author juan.medina-r
 */
public class HttpRequest {

    URI requri = null;

    HttpRequest(URI requri) {
        this.requri = requri;
    }

    public String getValue(String paramName) {

        // Extrae el valor de paramName desde el query.
        return requri.getQuery().split("=")[1]; // Ejemplo: /app/greeting?name=jhon
    }
}
