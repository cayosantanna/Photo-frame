package br.com.photoframe.compartilhado.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Utilitários para cálculo de hash MD5
 * Centraliza a lógica de integridade de arquivos
 */
public class HashUtil {
    
    /**
     * Calcula hash MD5 de um array de bytes
     * 
     * @param data bytes para calcular hash
     * @return string MD5 em hexadecimal maiúsculo, ou null se erro
     */
    public static String md5Hex(byte[] data) {
        if (data == null) return null;
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            
            // Converte cada byte para hexadecimal
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // MD5 sempre deve estar disponível
            throw new RuntimeException("MD5 não disponível", e);
        }
    }
    
    /**
     * Valida se hash calculado confere com esperado
     * 
     * @param data bytes do arquivo
     * @param expectedHash hash esperado (case-insensitive)
     * @return true se confere, false caso contrário
     */
    public static boolean validateHash(byte[] data, String expectedHash) {
        if (data == null || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        
        String currentHash = md5Hex(data);
        return currentHash != null && currentHash.equalsIgnoreCase(expectedHash.trim());
    }
}
