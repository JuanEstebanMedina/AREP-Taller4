package co.escuelaing.arep.microspringboot;

import java.io.IOException;
import java.net.URISyntaxException;

import co.escuelaing.arep.microspringboot.httpserver.HttpServer;

/**
 *
 * @author juan.medina-r
 */
public class MicroSpringBoot {

    public static void main(String[] args) throws IOException, URISyntaxException {
        System.out.println("Starting MicroSpringBoot:");
        HttpServer.runServer(getPort());
    }

    private static int getPort() {
        if (System.getenv("PORT") != null) {
            return Integer.parseInt(System.getenv("PORT"));
        }
        return 9000;
    }
}
