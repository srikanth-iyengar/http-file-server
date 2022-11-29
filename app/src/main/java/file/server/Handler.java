package file.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.*;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class Handler implements HttpHandler {
    private static final String environment = System.getenv("PROD");
    private static final String DATA = (environment==null?".":"~") + "/data";
    private static final String META = (environment==null?".":"~") + "/meta";
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "GET" -> doGet(exchange);
            case "POST" -> doPost(exchange);
            case "PUT" -> doPut(exchange);
            default -> doError(exchange, "Method not allowed", 405);
        }
        exchange.getResponseBody().flush();
        exchange.getResponseBody().close();
    }

    public Path[] getPath(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        if(uri.toString().contains("..")) {
            throw new RuntimeException();
        }
        Path path = (Path) Paths.get(uri.toString());
        return new Path[]{
                (Path) Paths.get(DATA + path.toString()),
                (Path) Paths.get(META + path.getParent().toString(), "meta.json")
        };
    }

    public void doError(HttpExchange exchange, String message, int httpStatus) throws IOException {
        OutputStream os = exchange.getResponseBody();
        exchange.sendResponseHeaders(httpStatus, message.length());
        os.write(message.getBytes());
        os.flush();
        os.close();
    }

    public void doGet(HttpExchange exchange) throws IOException {
        try {
            Path[] path = getPath(exchange);
            if(!Files.exists(path[0])) {
                throw new RuntimeException("Not found");
            }
            if(Files.isDirectory(path[0])) {
                throw new RuntimeException("Not Found");
            }
            String metaJson = Files.readString((path[1]));
            if(metaJson.length() == 0) {
                throw new RuntimeException("File not found");
            }
            JsonObject meta = JsonParser.parseString(metaJson).getAsJsonObject();
            exchange.getResponseHeaders().set("Content-Type", meta
                    .getAsJsonObject(path[0].toString())
                    .get("Content-Type")
                    .toString()
                    .replace("\"", "")
            );
            exchange.sendResponseHeaders(200, Files.size(path[0]));
            Files.copy(path[0], exchange.getResponseBody());
        }
        catch(Exception e) {
            doError(exchange, "Not Found", 404);
        }
    }

    public void doPost(HttpExchange exchange) throws IOException {
        try {
            Path[] path = getPath(exchange);
            Files.createDirectories(path[0].getParent());
            Files.createDirectories(path[1].getParent());
            Files.createFile(path[0]);
            JsonObject meta;
            if(!Files.exists(path[1])) {
                Files.createFile(path[1]);
                meta = new JsonObject();
            }
            else {
                meta = JsonParser.parseString(Files.readString(path[1])).getAsJsonObject();
            }
            JsonObject file = new JsonObject();
            file.addProperty("Content-Type", exchange.getRequestHeaders().get("Content-Type").get(0));
            meta.add(path[0].toString(), file);
            Files.writeString(path[1], meta.toString());
            Files.write(path[0], exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(201, path[0].toString().length());
            exchange.getResponseBody().write(path[0].toString().getBytes());
        }
        catch(Exception e) {
            doError(exchange, "File already exists", 409);
        }
    }

    public void doPut(HttpExchange exchange) throws IOException {
        try {
            Path[] path = getPath(exchange);
            if(!Files.exists(path[1])) {
                throw new RuntimeException("Not found");
            }
            JsonObject meta = JsonParser.parseString(Files.readString(path[1])).getAsJsonObject();
            meta.getAsJsonObject(
                    path[0].toString()).addProperty("Content-Type",
                    exchange.getRequestHeaders().get("Content-Type").get(0)
            );
            Files.writeString(path[1], meta.toString());
            Files.write(path[0], exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, path[0].toString().length());
            exchange.getResponseBody().write(path[0].toString().getBytes());
        }
        catch (Exception e) {
            e.printStackTrace();
            doError(exchange, "Not found", 404);
        }
    }
}
