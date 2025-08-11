package br.com.photoframe.compartilhado.core;

import java.util.Locale;

/**
 * Utilitários para normalização de datas
 * Converte diferentes formatos de data para formato padrão
 */
public class DateUtil {
    
    /**
     * Normaliza data aceita em diferentes formatos para prefixo padrão "yyyy/MM/dd/"
     * 
     * Aceita:
     * - yyyyMMdd (ex: 20250810)
     * - yyyy-MM-dd (ex: 2025-08-10) 
     * - yyyy/MM/dd (ex: 2025/08/10)
     * 
     * @param date string da data em um dos formatos aceitos
     * @return prefixo normalizado "yyyy/MM/dd/" ou null se inválido
     */
    public static String normalizeDatePrefix(String date) {
        if (date == null) return null;
        
        String d = date.trim();
        if (d.isEmpty()) return null;
        
        String y, m, dd;
        try {
            if (d.matches("\\d{8}")) { 
                // Formato yyyyMMdd
                y = d.substring(0, 4);
                m = d.substring(4, 6);
                dd = d.substring(6, 8);
            } else if (d.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // Formato yyyy-MM-dd
                y = d.substring(0, 4);
                m = d.substring(5, 7);
                dd = d.substring(8, 10);
            } else if (d.matches("\\d{4}/\\d{2}/\\d{2}")) {
                // Formato yyyy/MM/dd
                y = d.substring(0, 4);
                m = d.substring(5, 7);
                dd = d.substring(8, 10);
            } else {
                return null; // Formato não reconhecido
            }
            
            // Valida ranges de mês e dia
            int mi = Integer.parseInt(m);
            int di = Integer.parseInt(dd);
            if (mi < 1 || mi > 12 || di < 1 || di > 31) {
                return null;
            }
            
            // Retorna no formato padronizado
            return String.format(Locale.ROOT, "%s/%s/%s/", y, m, dd);
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
