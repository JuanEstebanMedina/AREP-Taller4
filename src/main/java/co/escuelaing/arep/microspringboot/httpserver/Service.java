package co.escuelaing.arep.microspringboot.httpserver;

/**
 *
 * @author juan.medina-r
 */
public interface Service {
    
    public String invoke(HttpRequest req, HttpResponse res);
    
}
