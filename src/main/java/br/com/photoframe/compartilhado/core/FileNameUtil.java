package br.com.photoframe.compartilhado.core;

import java.util.Locale;

/**
 * Utilitários para manipulação de nomes de arquivos
 * Garante segurança e padronização dos nomes
 */
public class FileNameUtil {
    
    /**
     * Sanitiza nome de arquivo removendo caracteres perigosos
     * Remove separadores de diretório e caracteres que podem causar problemas
     * 
     * @param name nome original do arquivo
     * @return nome sanitizado seguro para usar no sistema de arquivos
     */
    public static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "arquivo";
        }
        
        // Remove qualquer caminho que o cliente possa ter enviado
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        
        // Permitir letras, dígitos, espaço, -, _, ., (), e converter demais para '_'
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || 
                c == '_' || c == '.' || c == '(' || c == ')') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        
        String result = sb.toString();
        if (result.isBlank()) {
            result = "arquivo";
        }
        
        // Limita tamanho para evitar problemas em sistemas de arquivos
        if (result.length() > 80) {
            result = result.substring(result.length() - 80);
        }
        
        return result;
    }
    
    /**
     * Verifica se extensão do arquivo é permitida
     * 
     * @param fileName nome do arquivo
     * @return true se extensão é permitida
     */
    public static boolean isAllowedExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        
        String nameLower = fileName.toLowerCase(Locale.ROOT);
        int dot = nameLower.lastIndexOf('.');
        if (dot < 0) {
            return false; // Sem extensão
        }
        
        String ext = nameLower.substring(dot + 1);
    return ext.equals("jpg") || ext.equals("jpeg") || 
           ext.equals("png") || ext.equals("heic") || ext.equals("heif") ||
           ext.equals("mp4");
    }
    
    /**
     * Verifica se arquivo é vídeo baseado na extensão
     * 
     * @param fileName nome do arquivo
     * @return true se é vídeo
     */
    public static boolean isVideo(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4");
    }
}
