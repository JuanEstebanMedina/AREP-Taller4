package co.escuelaing.arep.microspringboot.httpserver;

import co.escuelaing.arep.microspringboot.annotations.GetMapping;
import co.escuelaing.arep.microspringboot.annotations.RequestParam;
import co.escuelaing.arep.microspringboot.annotations.RestController;
import java.net.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author juan.medina-r
 */
@SuppressWarnings("java:S2189")
public class HttpServer {

    private HttpServer() {
    }

    private static volatile boolean running = false;
    private static ServerSocket serverSocket;
    private static ExecutorService executorService;

    private static final String FILE_NOT_FOUND_RESPONSE = """
            HTTP/1.1 404 Not Found\r
            Content-Type: text/html; charset=utf-8\r
            \r
            <html><body><h1>404 - File Not Found</h1></body></html>
            """;
    private static final String DEFAULT_PATH = "/index.html";

    private static Map<String, Method> services = new HashMap<>();
    private static Path root = null; // /
    private static FileSystem jarFs = null;

    public static void loadServices(String packageName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');

            Enumeration<URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();

                if (resource.getProtocol().equals("file")) {
                    File file = new File(resource.getFile());
                    scanDirectory(file, packageName);
                }
            }
        } catch (IOException e) {
            Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    private static void scanDirectory(File directory, String packageName) {
        if (directory.exists()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    scanDirectory(file, packageName + "." + file.getName());
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." +
                            file.getName().substring(0, file.getName().length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(RestController.class)) {
                            registerController(clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        Logger.getLogger(HttpServer.class.getName())
                                .log(Level.SEVERE, null, e);
                    }
                }
            }
        }
    }

    private static void registerController(Class<?> controllerClass) {
        Method[] methods = controllerClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                String mapping = method.getAnnotation(GetMapping.class).value();
                services.put(mapping, method);
            }
        }
    }

    public static void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("Server socket closed. No new connections will be accepted.");
            } catch (IOException e) {
                Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, "Error closing server socket", e);
            }
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown of remaining tasks...");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, "Interrupted during shutdown", e);
            }
        }
    }

    public static void runServer(int port) throws IOException {
        if (root == null) {
            staticfilesFromClasspath("public");
        }
        loadServices("co.escuelaing.arep.microspringboot");
        System.out.println("Services loaded. Number of services: " + services.size());
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Ready on: http://localhost:" + port);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + port);
            throw e;
        }

        executorService = Executors.newFixedThreadPool(10);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered. Stopping server...");
            stopServer();
            System.out.println("Server stopped gracefully.");
        }));

        running = true;
        while (running && !serverSocket.isClosed()) {
            Socket clientSocket = null;
            try {
                System.out.println("Ready to listen ...");
                clientSocket = serverSocket.accept();
                Socket finalClientSocket = clientSocket;
                executorService.submit(() -> handleClient(finalClientSocket));
            } catch (IOException e) {
                if (!running || serverSocket.isClosed()) {
                    System.out.println("Server is shutting down, ignoring accept failure.");
                    break;
                }
                System.err.println("Accept failed: " + e.getMessage());
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (OutputStream rawOut = clientSocket.getOutputStream();
                PrintWriter out = new PrintWriter(rawOut, true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()))) {

            String inputLine;
            String outputLine;

            boolean isFirstLine = true;
            URI requesturi = null;

            while ((inputLine = in.readLine()) != null) {
                if (isFirstLine) {
                    requesturi = new URI(inputLine.split(" ")[1]);
                    System.out.println("Received: " + inputLine);
                    System.out.println("Path: " + requesturi.getPath());
                    isFirstLine = false;
                }
                // System.out.println("Received: " + inputLine);
                if (!in.ready()) {
                    break;
                }
            }

            if (requesturi == null) {
                outputLine = FILE_NOT_FOUND_RESPONSE;
                // System.out.println("Response: " + outputLine);
                out.println(outputLine);
            } else if (requesturi.getPath().startsWith("/api")) {
                outputLine = invokeService(requesturi);
                // System.out.println("Response: " + outputLine);
                out.println(outputLine);
            } else {
                Path file = mapToStaticFiles(requesturi.getPath());
                if (Files.exists(file) && !Files.isDirectory(file)) {
                    String contentType = detectContentType(file);
                    if (isTextContentType(contentType)) {
                        outputLine = fileRequestService(file);
                        // System.out.println("Response: " + outputLine);
                        out.println(outputLine);
                    } else {
                        serveBinaryFile(rawOut, file, contentType);
                    }
                } else {
                    outputLine = FILE_NOT_FOUND_RESPONSE;
                    // System.out.println("Response: " + outputLine);
                    out.println(outputLine);
                }
            }
        } catch (IOException | URISyntaxException e) {
            Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, null, e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, null, e);
            }
        }
    }

    private static String invokeService(URI requesturi) {
        String header = generalService("text/html");
        try {
            HttpRequest req = new HttpRequest(requesturi);
            // HttpResponse res = new HttpResponse();
            String key = requesturi.getPath().substring(4);

            System.out.println("Key: " + key);
            Method m = services.get(key);
            String[] argsValues = null;
            RequestParam rp = (RequestParam) m.getParameterAnnotations()[0][0];

            if (requesturi.getQuery() == null) {
                argsValues = new String[] { rp.defaultValue() };
            } else {
                String queryParamName = rp.value();
                argsValues = new String[] { req.getValue(queryParamName) };
            }
            return header + m.invoke(null, argsValues);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Logger.getLogger(HttpServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return header + "ERROR!";

    }

    private static Path mapToStaticFiles(String path) {
        if (path == null || path.equals("/")) {
            path = DEFAULT_PATH;
        }
        String normalized = path.replace("..", "");
        return root.resolve(normalized.substring(1)).normalize();
    }

    private static String generalService(String contentType) {
        return "HTTP/1.1 200 OK\r\n"
                + "content-type: " + contentType + "\r\n"
                + "\r\n";
    }

    private static String fileRequestService(Path file) throws IOException {
        String response = generalService(detectContentType(file));
        response = response + Files.readString(file, StandardCharsets.UTF_8);
        return response;

    }

    private static void serveBinaryFile(OutputStream rawOut, Path file, String contentType) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file);
        String header = "HTTP/1.1 200 OK\r\n"
                + "content-type: " + contentType + "\r\n"
                + "content-length: " + fileBytes.length + "\r\n"
                + "\r\n";
        rawOut.write(header.getBytes(StandardCharsets.UTF_8));
        rawOut.write(fileBytes);
        rawOut.flush();
    }

    private static String detectContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".ico")) {
            return "image/vnd.microsoft.icon";
        }
        return "application/octet-stream";
    }

    private static boolean isTextContentType(String contentType) {
        return contentType.startsWith("text/") || contentType.startsWith("application/javascript")
                || contentType.startsWith("application/json");
    }

    public static void staticfiles(String route) {
        try {
            if (route.startsWith("jar:")) {
                // jar:file:/.../app.jar!/public
                int bang = route.indexOf("!/");
                if (bang < 0)
                    throw new IllegalArgumentException("URL jar sin '!/': " + route);
                URI fsUri = URI.create(route.substring(0, bang)); // jar:file:/.../app.jar
                try {
                    jarFs = FileSystems.getFileSystem(fsUri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(fsUri, Map.of());
                }
                String inside = route.substring(bang + 1); // "public" o "public/..."
                root = jarFs.getPath("/" + (inside.startsWith("/") ? inside.substring(1) : inside))
                        .toAbsolutePath().normalize();
            } else if (route.startsWith("file:")) {
                root = Paths.get(URI.create(route)).toAbsolutePath().normalize();
            } else {
                root = Paths.get(route).toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid static root: " + route, e);
        }
    }

    public static void staticfilesFromClasspath(String resourceRoot) {
        try {
            URL url = HttpServer.class.getClassLoader().getResource(resourceRoot);
            if (url == null) {
                throw new IllegalStateException("No se encontró en classpath: " + resourceRoot);
            }
            switch (url.getProtocol()) {
                case "file" -> {
                    // IDE / clases sueltas
                    root = Paths.get(url.toURI()).toAbsolutePath().normalize();
                }
                case "jar" -> {
                    // Empaquetado: jar:file:/.../app.jar!/public
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    URI jarUri = conn.getJarFileURL().toURI(); // file:/.../app.jar
                    URI fsUri = URI.create("jar:" + jarUri.toString()); // jar:file:/.../app.jar
                    try {
                        jarFs = FileSystems.getFileSystem(fsUri);
                    } catch (FileSystemNotFoundException e) {
                        jarFs = FileSystems.newFileSystem(fsUri, Map.of());
                    }
                    String entry = conn.getEntryName(); // "public"
                    root = jarFs.getPath("/" + entry).toAbsolutePath().normalize();
                }
                default -> throw new IllegalStateException("Protocolo no soportado: " + url.getProtocol());
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar staticfiles desde classpath", e);
        }
    }
}
