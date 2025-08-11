package br.com.photoframe.servidor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.photoframe.compartilhado.core.FileNameUtil;
import br.com.photoframe.compartilhado.core.HashUtil;

/**
 * Gerenciador de arquivos e índices do servidor
 * Responsável por carregar, salvar e validar arquivos e metadados
 */
class GerenciadorArquivos {
    
    private static final String UPLOAD_DIR = "uploads";
    private static final Path OWNER_INDEX = Paths.get(UPLOAD_DIR, ".owners.tsv");
    private static final Path HASH_INDEX = Paths.get(UPLOAD_DIR, ".hashes.tsv");
    
    private final List<String> fileQueue = new ArrayList<>();
    private final Map<String, String> fileOwner = new HashMap<>();
    private final Map<String, String> fileMd5 = new HashMap<>();
    
    /**
     * Carrega arquivos existentes do diretório de uploads ao iniciar
     */
    void loadExistingFiles() {
        File dir = new File(UPLOAD_DIR);
        if (dir.exists() && dir.isDirectory()) {
            List<String> collected = new ArrayList<>();
            collectFilesRecursively(dir, dir, collected);
            Collections.sort(collected);
            fileQueue.clear();
            fileQueue.addAll(collected);
            System.out.println(fileQueue.size() + " arquivo(s) existentes carregados na fila.");
        }
    }
    
    /**
     * Coleta recursivamente todos os arquivos válidos de uma pasta
     */
    private static void collectFilesRecursively(File baseDir, File node, List<String> out) {
        File[] list = node.listFiles();
        if (list == null) return;
        
        for (File f : list) {
            if (f.isDirectory()) {
                collectFilesRecursively(baseDir, f, out);
            } else {
                String name = f.getName();
                if (name.startsWith(".")) continue; // ignora dotfiles
                
                String rel = baseDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                if (FileNameUtil.isAllowedExtension(rel)) {
                    out.add(rel);
                }
            }
        }
    }
    
    /**
     * Lê e valida integridade do arquivo antes de retornar bytes
     */
    byte[] tryReadValid(String relative) {
        if (relative == null || relative.isBlank()) return null;
        
        Path absolute = Paths.get(UPLOAD_DIR).resolve(relative);
        if (!Files.exists(absolute)) return null;
        
        try {
            byte[] bytes = Files.readAllBytes(absolute);
            String expected = fileMd5.get(relative);
            
            if (expected == null || expected.isBlank()) {
                // Primeira leitura: calcula e registra hash
                String md5 = HashUtil.md5Hex(bytes);
                fileMd5.put(relative, md5);
                saveHashIndex();
                return bytes;
            }
            
            // Validação de integridade
            if (HashUtil.validateHash(bytes, expected)) {
                return bytes;
            } else {
                System.err.println("[INTEGRIDADE] Arquivo rejeitado - hash diverge em " + relative);
                return null;
            }
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao ler arquivo: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Busca arquivo por nome em toda árvore de diretórios
     */
    static File findByFileName(File base, String name) {
        File[] list = base.listFiles();
        if (list == null) return null;
        
        for (File f : list) {
            if (f.isDirectory()) {
                File res = findByFileName(f, name);
                if (res != null) return res;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }
    
    /**
     * Carrega índice de donos dos arquivos
     */
    void loadOwnerIndex() {
        try {
            if (!Files.exists(OWNER_INDEX)) return;
            
            List<String> lines = Files.readAllLines(OWNER_INDEX, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                
                String rel = parts[0];
                String owner = parts[1];
                Path p = Paths.get(UPLOAD_DIR).resolve(rel);
                if (Files.exists(p)) {
                    fileOwner.put(rel, owner);
                }
            }
        } catch (IOException e) {
            System.err.println("Falha ao ler índice de donos: " + e.getMessage());
        }
    }
    
    /**
     * Salva índice de donos dos arquivos
     */
    void saveOwnerIndex() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            List<String> lines = fileOwner.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "\t" + e.getValue())
                    .collect(java.util.stream.Collectors.toList());
            Files.write(OWNER_INDEX, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Falha ao salvar índice de donos: " + e.getMessage());
        }
    }
    
    /**
     * Carrega índice de hashes dos arquivos
     */
    void loadHashIndex() {
        try {
            if (!Files.exists(HASH_INDEX)) return;
            
            List<String> lines = Files.readAllLines(HASH_INDEX, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                
                String rel = parts[0];
                String md5 = parts[1];
                Path p = Paths.get(UPLOAD_DIR).resolve(rel);
                if (Files.exists(p)) {
                    fileMd5.put(rel, md5);
                }
            }
        } catch (IOException e) {
            System.err.println("Falha ao ler índice de hashes: " + e.getMessage());
        }
    }
    
    /**
     * Salva índice de hashes dos arquivos
     */
    void saveHashIndex() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            List<String> lines = fileMd5.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "\t" + e.getValue())
                    .collect(java.util.stream.Collectors.toList());
            Files.write(HASH_INDEX, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Falha ao salvar índice de hashes: " + e.getMessage());
        }
    }
    
    // Getters para acesso controlado
    List<String> getFileQueue() { return fileQueue; }
    Map<String, String> getFileOwner() { return fileOwner; }
    Map<String, String> getFileMd5() { return fileMd5; }
}
