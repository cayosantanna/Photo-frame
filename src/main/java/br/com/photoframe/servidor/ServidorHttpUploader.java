package br.com.photoframe.servidor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.photoframe.compartilhado.PlaybackConfig;

/**
 * Servidor HTTP dedicado à página de upload e APIs REST simples.
 * Isola responsabilidades HTTP, deixando ServidorGaleria mais focado no RMI e regra de negócio.
 */
class ServidorHttpUploader {
    private final ServidorGaleria core;
    private HttpServer http;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastHit = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RATE_MS = 200L; // intervalo mínimo entre cliques por IP

    ServidorHttpUploader(ServidorGaleria core) { this.core = core; }

    void iniciar(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress(port), 0);
        // Rotas principais
        http.createContext("/uploader", this::uploaderPage);
        http.createContext("/upload", this::upload);
        http.createContext("/myfiles", this::meusArquivos);
        http.createContext("/allfiles", this::todosArquivos);
        http.createContext("/delete", this::excluirArquivo);
        http.createContext("/file", this::obterArquivo);
        http.createContext("/control", this::controles);
        http.createContext("/next", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,"Método não permitido","text/plain"); return; }
            if (isRateLimited(ex)) { send(ex, 429, "{\"status\":\"RATE_LIMIT\"}", "application/json"); return; }
            System.out.println("[WEB] /next acionado por " + ex.getRemoteAddress());
            core.next();
            send(ex,200,"{\"status\":\"OK\"}","application/json");
        });
        http.createContext("/previous", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,"Método não permitido","text/plain"); return; }
            if (isRateLimited(ex)) { send(ex, 429, "{\"status\":\"RATE_LIMIT\"}", "application/json"); return; }
            System.out.println("[WEB] /previous acionado por " + ex.getRemoteAddress());
            core.previous();
            send(ex,200,"{\"status\":\"OK\"}","application/json");
        });
        http.createContext("/health", ex -> send(ex,200,"OK","text/plain"));
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()*2));
        http.setExecutor(pool);
        http.start();
    }

    private void uploaderPage(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        String cid = getCookie(ex, "pf-client-id");
        if (cid == null || cid.isBlank()) {
            cid = "web-" + ip;
            ex.getResponseHeaders().add("Set-Cookie", "pf-client-id="+cid+"; Max-Age=31536000; Path=/; SameSite=Lax");
        }
        String html = readTextResource("web/uploader.html");
        if (html == null) { send(ex, 500, "Uploader não disponível", "text/plain; charset=UTF-8"); return; }
        send(ex, 200, html, "text/html; charset=UTF-8");
    }

    private void upload(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
        String query = ex.getRequestURI().getQuery();
        String filename = getQueryParam(query, "filename");
        String clientId = getQueryParam(query, "clientId");
        if (clientId == null || clientId.isBlank()) clientId = "web-" + ex.getRemoteAddress().getAddress().getHostAddress();
        if (filename == null || filename.isBlank()) filename = "upload.bin";
        byte[] body = readBodyLimited(ex.getRequestBody(), ServidorGaleria.MAX_BYTES + 1);
        if (body.length == 0 || body.length > ServidorGaleria.MAX_BYTES) { send(ex, 400, "Arquivo vazio ou maior que o limite.", "text/plain"); return; }
    boolean ok; try { ok = core.uploadFile(filename, body, clientId); } catch (Exception re) { ok = false; }
    System.out.println("[WEB] /upload de " + clientId + " arquivo=" + filename + " status=" + ok);
    send(ex, ok?200:400, ok?"{\"status\":\"OK\"}":"{\"status\":\"ERRO\"}", "application/json");
    }

    private void meusArquivos(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
        String cid = getQueryParam(ex.getRequestURI().getQuery(), "clientId");
        if (cid==null||cid.isBlank()) cid = getCookie(ex, "pf-client-id");
        if (cid==null||cid.isBlank()) cid = "web-" + ex.getRemoteAddress().getAddress().getHostAddress();
    System.out.println("[WEB] /myfiles de " + cid);
    List<String> list = core.getFileListByClient(cid);
        String json = toJsonArray(list);
        send(ex, 200, json, "application/json; charset=UTF-8");
    }

    private void todosArquivos(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
    System.out.println("[WEB] /allfiles");
    String json = toJsonArray(core.getFileList());
        send(ex, 200, json, "application/json; charset=UTF-8");
    }

    private void excluirArquivo(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
        String cid = getQueryParam(ex.getRequestURI().getQuery(), "clientId");
        if (cid==null||cid.isBlank()) cid = getCookie(ex, "pf-client-id");
        if (cid==null||cid.isBlank()) cid = "web-" + ex.getRemoteAddress().getAddress().getHostAddress();
        String path = getQueryParam(ex.getRequestURI().getQuery(), "path");
        if (cid==null||cid.isBlank()||path==null||path.isBlank()) { send(ex, 400, "Parâmetros inválidos", "text/plain"); return; }
    boolean ok = core.deleteFile(cid, path);
    System.out.println("[WEB] /delete cid=" + cid + " path=" + path + " status=" + ok);
    send(ex, ok?200:400, ok?"{\"status\":\"OK\"}":"{\"status\":\"ERRO\"}", "application/json");
    }

    private void obterArquivo(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "Método não permitido", "text/plain"); return; }
        String cid = getQueryParam(ex.getRequestURI().getQuery(), "clientId");
        if (cid==null||cid.isBlank()) cid = getCookie(ex, "pf-client-id");
        if (cid==null||cid.isBlank()) cid = "web-" + ex.getRemoteAddress().getAddress().getHostAddress();
        String rel = getQueryParam(ex.getRequestURI().getQuery(), "path");
        if (rel==null||rel.isBlank()) { send(ex, 400, "Parâmetros inválidos", "text/plain"); return; }
        byte[] bytes = core.readFileIfOwner(cid, rel);
        if (bytes == null) { send(ex, 404, "Não encontrado", "text/plain"); return; }
        String low = rel.toLowerCase(Locale.ROOT);
    String mime = low.endsWith(".png")?"image/png":
              (low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".heic")||low.endsWith(".heif"))?"image/jpeg":
              low.endsWith(".mp4")?"video/mp4":"application/octet-stream";
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", mime);
        h.set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (var os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void controles(HttpExchange ex) throws IOException {
        String m = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(m)) {
            System.out.println("[WEB] GET /control");
            PlaybackConfig c = core.getPlaybackConfig();
            String curDate = core.getDisplayDateFilter();
            String json = "{\"paused\":"+c.paused+",\"interval\":"+c.intervalMillis+",\"date\":\""+(curDate==null?"":curDate)+"\",\"forced\":\""+(c.forcedRelativePath==null?"":c.forcedRelativePath)+"\",\"loop\":"+c.loopVideo+",\"videoPaused\":"+c.videoPaused+",\"muted\":"+c.muted+"}";
            send(ex, 200, json, "application/json; charset=UTF-8");
            return;
        }
        if (!"POST".equalsIgnoreCase(m)) { send(ex, 405, "Método não permitido", "text/plain"); return; }
    String q = ex.getRequestURI().getQuery();
    if (isRateLimited(ex)) { send(ex, 429, "{\"status\":\"RATE_LIMIT\"}", "application/json"); return; }
        String paused = getQueryParam(q, "paused");
        String interval = getQueryParam(q, "interval");
        String date = getQueryParam(q, "date");
        String forced = getQueryParam(q, "forced");
        String loop = getQueryParam(q, "loop");
        String vpaused = getQueryParam(q, "videoPaused");
        String muted = getQueryParam(q, "muted");
    System.out.println("[WEB] POST /control paused="+paused+" interval="+interval+" date="+date+" forced="+forced+" loop="+loop+" videoPaused="+vpaused+" muted="+muted);
    if (paused != null) core.setPaused("true".equalsIgnoreCase(paused));
    if (interval != null) try { core.setPlaybackIntervalMillis(Integer.parseInt(interval)); } catch (NumberFormatException ignore) {}
    if (date != null) core.setDisplayDateFilter(date.isBlank()?null:date);
    if (forced != null) core.setForcedDisplayFile(forced.isBlank()?null:forced);
    if (loop != null) core.setLoopVideo("true".equalsIgnoreCase(loop));
    if (vpaused != null) core.setVideoPaused("true".equalsIgnoreCase(vpaused));
    if (muted != null) core.setMuted("true".equalsIgnoreCase(muted));
    send(ex, 200, "{\"status\":\"OK\"}", "application/json");
    }

    // Utilidades HTTP (copiadas do core para evitar acesso direto a campos privados)
    static void send(HttpExchange ex, int status, String content, String contentType) throws IOException { byte[] bytes = content.getBytes(StandardCharsets.UTF_8); Headers h = ex.getResponseHeaders(); h.set("Content-Type", contentType); h.set("Cache-Control", "no-store"); ex.sendResponseHeaders(status, bytes.length); try (var os = ex.getResponseBody()) { os.write(bytes); } }
    private static String readTextResource(String cp) throws IOException { try (InputStream in = ServidorHttpUploader.class.getClassLoader().getResourceAsStream(cp)) { if (in == null) return null; return new String(in.readAllBytes(), StandardCharsets.UTF_8); } }
    static String getCookie(HttpExchange ex, String name) { List<String> cookies = ex.getRequestHeaders().get("Cookie"); if (cookies == null) return null; for (String header : cookies) { String[] parts = header.split("; "); for (String p : parts) { int i = p.indexOf('='); if (i>0) { String k = p.substring(0,i); String v = p.substring(i+1); if (name.equals(k)) return v; } } } return null; }
    static String toJsonArray(List<String> list) { StringBuilder sb = new StringBuilder(); sb.append('['); for (int i=0;i<list.size();i++) { if (i>0) sb.append(','); sb.append('"').append(list.get(i).replace("\\","/").replace("\"","%22")).append('"'); } sb.append(']'); return sb.toString(); }
    static byte[] readBodyLimited(InputStream in, long max) throws IOException { try { ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; long total = 0; int r; while ((r = in.read(buf)) != -1) { total += r; if (total > max) break; bos.write(buf, 0, r); } return bos.toByteArray(); } finally { try { in.close(); } catch (IOException ignore) {} } }
    static String getQueryParam(String query, String key) { if (query == null) return null; String[] parts = query.split("&"); for (String p : parts) { int i = p.indexOf('='); if (i <= 0) continue; String k = urlDecode(p.substring(0, i)); if (key.equals(k)) return urlDecode(p.substring(i + 1)); } return null; }
    static String urlDecode(String s) { try { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (java.io.UnsupportedEncodingException e) { return s; } }
    private boolean isRateLimited(HttpExchange ex) {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        Long prev = lastHit.put(ip, now);
        return prev != null && (now - prev) < RATE_MS;
    }
}
