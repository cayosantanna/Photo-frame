package br.com.photoframe.servidor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.photoframe.compartilhado.GaleriaRemota;
import br.com.photoframe.compartilhado.PlaybackConfig;
import br.com.photoframe.compartilhado.core.FileNameUtil;
import br.com.photoframe.compartilhado.core.HashUtil;

// Servidor HTTP movido para classe dedicada ServidorHttpUploader

public class ServidorGaleria extends UnicastRemoteObject implements GaleriaRemota {
    private static final String UPLOAD_DIR = "uploads";
    private static final Path OWNER_INDEX = Paths.get(UPLOAD_DIR, ".owners.tsv");
    private static final Path HASH_INDEX = Paths.get(UPLOAD_DIR, ".hashes.tsv");
    private final List<String> fileQueue = new ArrayList<>();
    private int currentFileIndex = -1;
    static final long MAX_BYTES = 100L * 1024 * 1024; // 100 MB (visível no pacote)
    private final Map<String, String> fileOwner = new HashMap<>();
    private final Map<String, String> fileMd5 = new HashMap<>();
    private final Map<String, Integer> dateLastIndex = new HashMap<>();
    private String globalDateFilterPrefix = null;
    private final PlaybackConfig playback = new PlaybackConfig();
    private long lastSwitchAt = 0L;
    private String lastServedRel = null;
    private boolean holdCurrentUntilVideoEnds = false;
    // Fila simples de comandos para suavizar picos de chamadas
    private final java.util.concurrent.BlockingQueue<Runnable> commandQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    private final Thread commandWorker = new Thread(() -> {
        while (true) {
            try {
                Runnable r = commandQueue.take();
                try { r.run(); } catch (Throwable t) { System.err.println("[CMD] Erro ao executar comando: "+t); }
            } catch (InterruptedException ie) { /* continua */ }
        }
    }, "pf-cmd-worker");

    public ServidorGaleria() throws RemoteException {
        loadExistingFiles();
        loadOwnerIndex();
        loadHashIndex();
    try { commandWorker.setDaemon(true); commandWorker.start(); } catch (Exception ignore) {}
    }

    // Log simples com horário para comandos recebidos
    private static void logCmd(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        System.out.println("[CMD " + ts + "] " + msg);
    }

    @Override
    public synchronized boolean uploadFile(String fileName, byte[] fileData, String clientId) throws RemoteException {
        if (fileName == null || fileData == null) return false;
        if (fileData.length == 0 || fileData.length > MAX_BYTES) { System.err.println("Upload rejeitado: tamanho inválido"); return false; }
        fileName = FileNameUtil.sanitizeFileName(fileName);
        if (!FileNameUtil.isAllowedExtension(fileName)) { System.err.println("Upload rejeitado: extensão não permitida para: " + fileName); return false; }
        Date now = new Date();
        String year = new SimpleDateFormat("yyyy", Locale.ROOT).format(now);
        String month = new SimpleDateFormat("MM", Locale.ROOT).format(now);
        String day = new SimpleDateFormat("dd", Locale.ROOT).format(now);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(now);
        String uniqueFileName = timestamp + "_" + fileName;
        Path dayDir = Paths.get(UPLOAD_DIR, year, month, day);
        try { Files.createDirectories(dayDir); } catch (IOException e) { System.err.println("Falha ao criar diretórios: " + e.getMessage()); return false; }
        Path filePath = dayDir.resolve(uniqueFileName);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(fileData);
            String md5Hash = HashUtil.md5Hex(fileData);
            System.out.println("UPLOAD Recebido! cliente="+clientId+" arquivo="+filePath+" bytes="+fileData.length+" md5="+md5Hash);
            String relativePath = Paths.get(UPLOAD_DIR).relativize(filePath).toString().replace('\\', '/');
            fileQueue.add(relativePath);
            Collections.sort(fileQueue);
            if (clientId != null && !clientId.isBlank()) fileOwner.put(relativePath, clientId);
            fileMd5.put(relativePath, md5Hash);
            saveOwnerIndex(); saveHashIndex();
            return true;
        } catch (IOException e) { System.err.println("Falha ao salvar arquivo: " + e.getMessage()); return false; }
    }

    // Lista todos os caminhos relativos (exibível por qualquer cliente)
    @Override public synchronized List<String> getFileList() {
        logCmd("getFileList() chamado");
        return new ArrayList<>(fileQueue);
    }
    // Lista apenas os caminhos relativos pertencentes a um determinado cliente
    @Override public synchronized List<String> getFileListByClient(String clientId) {
        logCmd("getFileListByClient(clientId=" + clientId + ")");
        if (clientId==null||clientId.isBlank()) return List.of();
        List<String> out=new ArrayList<>();
        for(String rel:fileQueue){ if (clientId.equals(fileOwner.get(rel))) out.add(rel);} return out;
    }

    // Lê bytes de um arquivo se o cliente informado for o dono; caso contrário retorna null
    // Este método garante que apenas o dono do arquivo pode acessá-lo
    @Override public synchronized byte[] readFileIfOwner(String clientId, String relativePath) {
        logCmd("readFileIfOwner(clientId=" + clientId + ", path=" + relativePath + ")");
        // Validação básica: cliente e caminho devem estar preenchidos
        if (clientId==null||clientId.isBlank()||relativePath==null||relativePath.isBlank()) return null;
        
        // Busca o dono registrado do arquivo
        String owner = fileOwner.get(relativePath);
        
        // Se não há dono registrado ou o cliente não é o dono, nega acesso
        if (owner==null || !owner.equals(clientId)) return null;
        
        // Se passou na validação de propriedade, lê o arquivo com verificação de integridade
        return tryReadValid(relativePath);
    }

    /**
     * Método principal que retorna próximo arquivo para exibição no viewer
     * Este é o coração do slideshow - decide qual arquivo entregar baseado em:
     * - Estado de pausa
     * - Arquivo forçado (prioritário)
     * - Filtro de data
     * - Rotação normal da fila
     * - Tempo desde última troca
     */
    @Override
    public synchronized byte[] getNextDisplayFile() throws RemoteException {
        // Se não há arquivos, não há o que exibir
        if (fileQueue.isEmpty()) { 
            System.out.println("[DEBUG] Fila vazia - nenhum arquivo para exibir");
            return null; 
        }
        
        // Se slideshow está pausado, não avança
        if (playback.paused) {
            System.out.println("[DEBUG] Slideshow pausado");
            return null;
        }
        
        // PRIORIDADE 1: Arquivo forçado (quando usuário seleciona arquivo específico)
        if (playback.forcedRelativePath != null) {
            // Se o tipo não é suportado para exibição, ignora e limpa
            if (!isDisplayable(playback.forcedRelativePath)) {
                System.err.println("[DEBUG] Arquivo forçado não suportado para exibição: " + playback.forcedRelativePath);
                playback.forcedRelativePath = null;
            } else {
                byte[] forced = tryReadValid(playback.forcedRelativePath);
                if (forced != null) {
                    // Arquivo forçado válido - exibe este
                    lastServedRel = playback.forcedRelativePath;
                    holdCurrentUntilVideoEnds = isVideo(lastServedRel);
                    lastSwitchAt = System.currentTimeMillis();
                    System.out.println("[DEBUG] Exibindo arquivo forçado: " + playback.forcedRelativePath);
                    return forced;
                } else {
                    // Arquivo forçado inválido/corrompido - limpa configuração
                    System.err.println("[INTEGRIDADE] Arquivo forçado inválido: " + playback.forcedRelativePath);
                    playback.forcedRelativePath = null;
                }
            }
        }
        
        // Calcula se é hora de trocar de arquivo
        long now = System.currentTimeMillis();
        boolean shouldSwitch = !holdCurrentUntilVideoEnds && 
                              (now - lastSwitchAt) >= Math.max(1000, playback.intervalMillis);
        
        // PRIORIDADE 2: Filtro de data (quando usuário filtra por dia específico)
        if (globalDateFilterPrefix != null) {
            if (shouldSwitch || lastServedRel == null) {
                byte[] byDate = getNextDisplayFileByDate(globalDateFilterPrefix);
                if (byDate != null) { 
                    lastSwitchAt = now;
                    System.out.println("[DEBUG] Exibindo por filtro de data: " + globalDateFilterPrefix);
                    return byDate; 
                }
            }
            // Se não é hora de trocar e já temos algo, mantém atual (não reenvia)
            if (lastServedRel != null) { 
                return null; // Não reenvia bytes - Viewer mantém conteúdo atual
            }
        }
        
        // PRIORIDADE 3: Rotação normal da fila
        if (shouldSwitch || lastServedRel == null) {
            int tries = 0;
            while (tries < fileQueue.size()) {
                // Avança para próximo arquivo na fila (circular)
                currentFileIndex = (currentFileIndex + 1) % fileQueue.size();
                String relative = fileQueue.get(currentFileIndex);

                // Ignora tipos não suportados para exibição (ex.: HEIC/HEIF)
                if (!isDisplayable(relative)) {
                    System.out.println("[DEBUG] Ignorando não suportado para exibição: " + relative);
                    tries++;
                    continue;
                }
                
                System.out.println("[DEBUG] Tentando arquivo: " + relative);
                
                // Tenta ler e validar arquivo
                byte[] b = tryReadValid(relative);
                if (b != null) {
                    // Arquivo válido - sucesso!
                    lastSwitchAt = now;
                    lastServedRel = relative;
                    holdCurrentUntilVideoEnds = isVideo(relative);
                    System.out.println("[DEBUG] Enviando para Display: " + Paths.get(UPLOAD_DIR).resolve(relative).getFileName());
                    return b;
                } else {
                    // Arquivo inválido/corrompido - remove da fila e tenta próximo
                    System.err.println("[INTEGRIDADE] Removendo arquivo inválido da fila: " + relative);
                    removeFromIndexes(relative, currentFileIndex);
                    // Ajusta índice para reavaliar posição atual
                    currentFileIndex = (currentFileIndex - 1 + Math.max(1, fileQueue.size())) % Math.max(1, fileQueue.size());
                }
                tries++;
                if (fileQueue.isEmpty()) break; // Se fila ficou vazia, para
            }
        }
        
        // Se chegou aqui e não é hora de trocar, mantém atual (não reenvia)
        if (lastServedRel != null) { 
            return null; // Evita reenvio desnecessário
        }
        
        // Não há nada válido para exibir
        System.out.println("[DEBUG] Nenhum arquivo válido encontrado");
        return null;
    }    private static boolean isVideo(String relativePath) { String p = relativePath.toLowerCase(Locale.ROOT); return p.endsWith(".mp4"); }
    private static boolean isDisplayable(String relativePath) {
        String p = relativePath.toLowerCase(Locale.ROOT);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".mp4");
    }
    @Override public void next() {
        logCmd("next() enfileirado");
        commandQueue.offer(() -> {
            synchronized (ServidorGaleria.this) {
                holdCurrentUntilVideoEnds = false;
                lastSwitchAt = 0L;
            }
        });
    }
    @Override public void previous() {
        logCmd("previous() enfileirado");
        commandQueue.offer(() -> {
            synchronized (ServidorGaleria.this) {
                if (fileQueue.isEmpty()) return; 
                currentFileIndex = (currentFileIndex - 2 + fileQueue.size()) % fileQueue.size();
                holdCurrentUntilVideoEnds = false;
                lastSwitchAt = 0L;
            }
        });
    }

    @Override
    public synchronized byte[] getNextDisplayFileByDate(String date) throws RemoteException {
    logCmd("getNextDisplayFileByDate(date=" + date + ")");
        String prefix = normalizeDatePrefix(date); if (prefix == null) return null; if (fileQueue.isEmpty()) return null;
        int start = dateLastIndex.getOrDefault(prefix, -1) + 1; if (start >= fileQueue.size()) start = 0; int tries = 0;
        while (tries < fileQueue.size()) {
            int idx = (start + tries) % fileQueue.size(); String rel = fileQueue.get(idx);
            if (rel.startsWith(prefix) && isDisplayable(rel)) { byte[] bytes = tryReadValid(rel); if (bytes != null) { dateLastIndex.put(prefix, idx); lastServedRel = rel; holdCurrentUntilVideoEnds = isVideo(rel); return bytes; } else { removeFromIndexes(rel, idx); continue; } }
            tries++;
        }
        return null;
    }

    @Override
    public synchronized boolean verifyFileIntegrity(String fileName, String hash) throws RemoteException {
    logCmd("verifyFileIntegrity(fileName=" + fileName + ")");
        String normalizedExpected = hash == null ? null : hash.replace(" ", "").toLowerCase(Locale.ROOT);
        if (normalizedExpected == null || normalizedExpected.isEmpty()) return false;
        Path candidate = Paths.get(UPLOAD_DIR).resolve(fileName);
        if (!Files.exists(candidate)) { File base = new File(UPLOAD_DIR); File found = findByFileName(base, new File(fileName).getName()); if (found != null) candidate = found.toPath(); }
        if (!Files.exists(candidate)) return false;
        try (FileInputStream fis = new FileInputStream(candidate.toFile())) { byte[] buf = fis.readAllBytes(); String md5 = HashUtil.md5Hex(buf).toLowerCase(Locale.ROOT); return md5.equals(normalizedExpected); }
        catch (IOException e) { return false; }
    }

    @Override
    public synchronized boolean deleteFile(String clientId, String relativePath) throws RemoteException {
    logCmd("deleteFile(clientId=" + clientId + ", path=" + relativePath + ")");
        if (clientId == null || clientId.isBlank() || relativePath == null || relativePath.isBlank()) return false;
        String owner = fileOwner.get(relativePath); if (owner == null || !owner.equals(clientId)) return false;
        int idx = fileQueue.indexOf(relativePath); if (idx < 0) return false;
        Path absolute = Paths.get(UPLOAD_DIR).resolve(relativePath);
        try { Files.deleteIfExists(absolute); } catch (IOException e) { return false; }
        fileQueue.remove(idx); fileOwner.remove(relativePath); fileMd5.remove(relativePath);
        if (playback.forcedRelativePath != null && playback.forcedRelativePath.equals(relativePath)) playback.forcedRelativePath = null;
        if (lastServedRel != null && lastServedRel.equals(relativePath)) lastServedRel = null;
        if (fileQueue.isEmpty()) currentFileIndex = -1; else if (idx <= currentFileIndex) currentFileIndex = Math.max(-1, currentFileIndex - 1);
        if (!dateLastIndex.isEmpty()) { Map<String,Integer> copy = new HashMap<>(dateLastIndex); for (Map.Entry<String,Integer> e : copy.entrySet()) { Integer v = e.getValue(); if (v != null && v >= idx) { dateLastIndex.put(e.getKey(), Math.max(-1, v - 1)); } } }
        saveOwnerIndex(); saveHashIndex();
        return true;
    }

    // Sinalizações de controle vindas do cliente (web/desktop)
    @Override public synchronized void setDisplayDateFilter(String date) {
        logCmd("setDisplayDateFilter(" + date + ")");
        this.globalDateFilterPrefix = normalizeDatePrefix(date);
    }
    @Override public synchronized String getDisplayDateFilter() {
        logCmd("getDisplayDateFilter() => " + this.globalDateFilterPrefix);
        return this.globalDateFilterPrefix;
    }
    @Override public synchronized void setPaused(boolean paused) {
        logCmd("setPaused(" + paused + ")");
        playback.paused = paused;
        if (!paused) { // ao despausar, permite troca imediata se necessário
            lastSwitchAt = 0L;
        }
    }
    @Override public synchronized void setPlaybackIntervalMillis(int ms) {
        int old = playback.intervalMillis;
        if (ms >= 1000 && ms <= 600_000) playback.intervalMillis = ms;
        logCmd("setPlaybackIntervalMillis(" + ms + ") efetivo=" + playback.intervalMillis + " (antes=" + old + ")");
    }
    @Override public synchronized void setForcedDisplayFile(String relativePath) {
        logCmd("setForcedDisplayFile(" + relativePath + ")");
        playback.forcedRelativePath = (relativePath==null||relativePath.isBlank())?null:relativePath;
        // força troca imediata
        lastSwitchAt = 0L;
        holdCurrentUntilVideoEnds = false;
    }
    @Override public synchronized PlaybackConfig getPlaybackConfig() {
        // Silencia log de alta frequência para reduzir ruído
        return playback.copy();
    }
    @Override public synchronized void setLoopVideo(boolean loop) {
        logCmd("setLoopVideo(" + loop + ")");
        playback.loopVideo = loop;
    }
    @Override public synchronized void setVideoPaused(boolean paused) {
        logCmd("setVideoPaused(" + paused + ")");
        playback.videoPaused = paused;
    }
    @Override public synchronized void setMuted(boolean muted) {
        logCmd("setMuted(" + muted + ")");
        playback.muted = muted;
    }

    private void loadExistingFiles() {
        File dir = new File(UPLOAD_DIR);
        if (dir.exists() && dir.isDirectory()) {
            List<String> collected = new ArrayList<>();
            collectFilesRecursively(dir, dir, collected);
            Collections.sort(collected);
            fileQueue.clear(); fileQueue.addAll(collected);
        }
    }

    private static void collectFilesRecursively(File baseDir, File node, List<String> out) {
        File[] list = node.listFiles(); if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectFilesRecursively(baseDir, f, out);
            else { String name = f.getName(); if (name.startsWith(".")) continue; String rel = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/'); String low = rel.toLowerCase(Locale.ROOT); if (low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".png")||low.endsWith(".mp4")) out.add(rel); }
        }
    }

    private static File findByFileName(File base, String name) {
        File[] list = base.listFiles(); if (list == null) return null; for (File f : list) { if (f.isDirectory()) { File res = findByFileName(f, name); if (res != null) return res; } else if (f.getName().equals(name)) { return f; } } return null;
    }

    /**
     * Método crítico: lê e valida integridade do arquivo antes de entregar
     * Este método garante que apenas arquivos íntegros (não corrompidos/alterados) sejam exibidos
     * 
     * @param relative Caminho relativo do arquivo dentro da pasta uploads
     * @return bytes do arquivo se válido, null se corrompido ou inexistente
     */
    private synchronized byte[] tryReadValid(String relative) {
        // Validação básica: caminho deve estar preenchido
        if (relative == null || relative.isBlank()) return null;
        
        // Constrói caminho absoluto combinando pasta uploads com caminho relativo
        Path absolute = Paths.get(UPLOAD_DIR).resolve(relative);
        
        // Se arquivo não existe fisicamente, retorna null
        if (!Files.exists(absolute)) { return null; }
        
        try {
            // Lê todos os bytes do arquivo para memória
            byte[] bytes = Files.readAllBytes(absolute);
            
            // Busca o hash MD5 esperado (calculado quando arquivo foi enviado)
            String expected = fileMd5.get(relative);
            
            // Se não temos hash esperado, calcula e registra pela primeira vez
            if (expected == null || expected.isBlank()) {
                String md5 = HashUtil.md5Hex(bytes);
                fileMd5.put(relative, md5);
                saveHashIndex(); // Persiste o hash no arquivo .hashes.tsv
                return bytes;
            }
            
            // VALIDAÇÃO DE INTEGRIDADE: calcula hash atual e compara com esperado
            String currentMd5 = HashUtil.md5Hex(bytes);
            if (currentMd5.equalsIgnoreCase(expected)) {
                // Hash confere: arquivo está íntegro, pode exibir
                return bytes;
            } else {
                // Hash diverge: arquivo foi alterado/corrompido, NÃO EXIBE
                System.err.println("[INTEGRIDADE] Arquivo rejeitado - hash diverge em " + relative);
                System.err.println("  Esperado: " + expected);
                System.err.println("  Atual: " + currentMd5);
                return null;
            }
        } catch (IOException e) { 
            // Erro ao ler arquivo
            System.err.println("[ERRO] Falha ao ler arquivo: " + e.getMessage());
            return null; 
        }
    }

    public static void main(String[] args) {
        try {
            // Define o hostname RMI para o IPv4 local da máquina quando possível
            try {
                String lan = findLanIPv4();
                if (lan != null && System.getProperty("java.rmi.server.hostname") == null) {
                    System.setProperty("java.rmi.server.hostname", lan);
                }
            } catch (Exception ignore) {}
            Registry registry;
            try { registry = LocateRegistry.getRegistry(1099); registry.list(); }
            catch (RemoteException e) { try { registry = LocateRegistry.createRegistry(1099); } catch (RemoteException ex) { registry = LocateRegistry.getRegistry(1099); } }
            ServidorGaleria server = new ServidorGaleria();
            registry.rebind("GaleriaService", server);
            System.out.println("Servidor iniciado.");
            int httpPort = 18080;
            String httpProp = System.getProperty("httpPort");
            if (httpProp != null && !httpProp.isBlank()) { try { httpPort = Integer.parseInt(httpProp.trim()); } catch (NumberFormatException ignore) {} }
            else if (args != null && args.length > 0) { try { httpPort = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {} }
            server.startHttpUploaderServer(httpPort);
            try {
                String lan = findLanIPv4();
                if (lan != null) {
                    System.out.println("Acesse o uploader em: http://"+lan+":"+httpPort+"/uploader");
                }
            } catch (Exception ignore) {}
            
            // Inicia automaticamente o visualizador (tela de exibição)
            System.out.println("Iniciando visualizador...");
            startVisualizador();
            
        } catch (RemoteException e) { System.err.println("Falha RMI no servidor: " + e); }
    }

    private static String findLanIPv4() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) {}
        try {
            java.net.InetAddress local = java.net.InetAddress.getLocalHost();
            if (local instanceof java.net.Inet4Address) return local.getHostAddress();
        } catch (Exception ignore) {}
        return null;
    }
    
    private static void startVisualizador() {
        try {
            // Inicia o visualizador em uma thread separada
            new Thread(() -> {
                try {
                    // Aguarda um pouco para o servidor terminar de inicializar
                    Thread.sleep(1000);
                    br.com.photoframe.servidor.display.Visualizador.main(new String[0]);
                } catch (Exception e) {
                    System.err.println("Erro ao iniciar visualizador: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            System.err.println("Erro ao criar thread do visualizador: " + e.getMessage());
        }
    }

    // métodos duplicados removidos (as versões com logs já estão definidas acima)

    private void startHttpUploaderServer(int port) {
        try {
            new ServidorHttpUploader(this).iniciar(port);
        } catch (IOException e) {
            System.err.println("Falha ao iniciar HTTP Uploader: " + e.getMessage());
        }
    }

    // Utilidades HTTP movidas para ServidorHttpUploader; HTML do uploader passou a ser recurso estático em resources/web/uploader.html

    private synchronized void loadOwnerIndex() { try { if (!Files.exists(OWNER_INDEX)) return; List<String> lines = Files.readAllLines(OWNER_INDEX, StandardCharsets.UTF_8); for (String line : lines) { if (line.isBlank() || line.startsWith("#")) continue; String[] parts = line.split("\t"); if (parts.length < 2) continue; String rel = parts[0]; String owner = parts[1]; Path p = Paths.get(UPLOAD_DIR).resolve(rel); if (Files.exists(p)) { fileOwner.put(rel, owner); } } } catch (IOException e) { System.err.println("Falha ao ler índice de donos: "+e.getMessage()); } }
    private synchronized void saveOwnerIndex() { try { Files.createDirectories(Paths.get(UPLOAD_DIR)); List<String> lines = fileOwner.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey()+"\t"+e.getValue()).collect(java.util.stream.Collectors.toList()); Files.write(OWNER_INDEX, lines, StandardCharsets.UTF_8); } catch (IOException e) { System.err.println("Falha ao salvar índice de donos: "+e.getMessage()); } }
    private synchronized void loadHashIndex() { try { if (!Files.exists(HASH_INDEX)) return; List<String> lines = Files.readAllLines(HASH_INDEX, StandardCharsets.UTF_8); for (String line : lines) { if (line.isBlank() || line.startsWith("#")) continue; String[] parts = line.split("\t"); if (parts.length < 2) continue; String rel = parts[0]; String md5 = parts[1]; Path p = Paths.get(UPLOAD_DIR).resolve(rel); if (Files.exists(p)) { fileMd5.put(rel, md5); } } } catch (IOException e) { System.err.println("Falha ao ler índice de hashes: "+e.getMessage()); } }
    private synchronized void saveHashIndex() { try { Files.createDirectories(Paths.get(UPLOAD_DIR)); List<String> lines = fileMd5.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey()+"\t"+e.getValue()).collect(java.util.stream.Collectors.toList()); Files.write(HASH_INDEX, lines, StandardCharsets.UTF_8); } catch (IOException e) { System.err.println("Falha ao salvar índice de hashes: "+e.getMessage()); } }

    private synchronized void removeFromIndexes(String rel, int idxHint) {
        if (rel == null) return; int idx = idxHint; if (idx < 0 || idx >= fileQueue.size() || !rel.equals(fileQueue.get(idx))) { idx = fileQueue.indexOf(rel); }
        if (idx >= 0) { fileQueue.remove(idx); }
        fileOwner.remove(rel); fileMd5.remove(rel);
        if (fileQueue.isEmpty()) currentFileIndex = -1; else if (idx >= 0 && idx <= currentFileIndex) currentFileIndex = Math.max(-1, currentFileIndex - 1);
        if (!dateLastIndex.isEmpty()) { Map<String,Integer> copy = new HashMap<>(dateLastIndex); for (Map.Entry<String,Integer> e : copy.entrySet()) { Integer v = e.getValue(); if (v != null && idx >= 0 && v >= idx) { dateLastIndex.put(e.getKey(), Math.max(-1, v - 1)); } } }
        if (rel.equals(lastServedRel)) lastServedRel = null;
        if (playback.forcedRelativePath != null && playback.forcedRelativePath.equals(rel)) playback.forcedRelativePath = null;
        saveOwnerIndex(); saveHashIndex();
    }

    

    static String normalizeDatePrefix(String date) {
        if (date == null) return null; String d = date.trim(); if (d.isEmpty()) return null; String y, m, dd;
        try {
            if (d.matches("\\d{8}")) { y = d.substring(0,4); m = d.substring(4,6); dd = d.substring(6,8); }
            else if (d.matches("\\d{4}-\\d{2}-\\d{2}")) { y = d.substring(0,4); m = d.substring(5,7); dd = d.substring(8,10); }
            else if (d.matches("\\d{4}/\\d{2}/\\d{2}")) { y = d.substring(0,4); m = d.substring(5,7); dd = d.substring(8,10); }
            else { return null; }
            int mi = Integer.parseInt(m); int di = Integer.parseInt(dd); if (mi < 1 || mi > 12 || di < 1 || di > 31) return null; return String.format(Locale.ROOT, "%s/%s/%s/", y, m, dd);
        } catch (NumberFormatException e) { return null; }
    }
}
