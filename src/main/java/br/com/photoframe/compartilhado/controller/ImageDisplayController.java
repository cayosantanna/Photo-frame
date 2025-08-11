package br.com.photoframe.compartilhado.controller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Controlador para exibição de imagens
 * Gerencia o redimensionamento e exibição de imagens em componentes Swing
 */
public class ImageDisplayController {
    
    /**
     * Cria um componente para exibir uma imagem a partir de bytes
     * 
     * @param imageBytes dados da imagem
     * @param containerSize tamanho do container onde será exibida
     * @return JLabel com a imagem redimensionada ou null se erro
     */
    public static JLabel createImageLabel(byte[] imageBytes, Dimension containerSize) {
        if (imageBytes == null || imageBytes.length == 0) {
            return createErrorLabel("Dados de imagem inválidos");
        }
        
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) {
                return createErrorLabel("Formato de imagem não suportado");
            }
            
            // Calcular dimensões mantendo proporção
            Dimension scaledSize = calculateScaledSize(originalImage, containerSize);
            
            // Redimensionar imagem
            Image scaledImage = originalImage.getScaledInstance(
                scaledSize.width, 
                scaledSize.height, 
                Image.SCALE_SMOOTH
            );
            
            JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            
            return imageLabel;
            
        } catch (IOException e) {
            System.err.println("Erro ao processar imagem: " + e.getMessage());
            return createErrorLabel("Erro ao carregar imagem");
        }
    }
    
    /**
     * Calcula o tamanho redimensionado mantendo a proporção da imagem
     * 
     * @param originalImage imagem original
     * @param containerSize tamanho do container
     * @return dimensões calculadas
     */
    private static Dimension calculateScaledSize(BufferedImage originalImage, Dimension containerSize) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        int containerWidth = containerSize.width;
        int containerHeight = containerSize.height;
        
        // Calcular fatores de escala
        double scaleX = (double) containerWidth / originalWidth;
        double scaleY = (double) containerHeight / originalHeight;
        
        // Usar o menor fator para manter proporção
        double scale = Math.min(scaleX, scaleY);
        
        // Calcular novas dimensões
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        return new Dimension(newWidth, newHeight);
    }
    
    /**
     * Cria um label com mensagem de erro
     * 
     * @param message mensagem de erro
     * @return JLabel com a mensagem
     */
    private static JLabel createErrorLabel(String message) {
        JLabel errorLabel = new JLabel(message);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorLabel.setVerticalAlignment(SwingConstants.CENTER);
        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        return errorLabel;
    }
    
    /**
     * Verifica se os dados representam uma imagem válida
     * 
     * @param imageBytes dados para verificar
     * @return true se é uma imagem válida
     */
    public static boolean isValidImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            return image != null;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Cria um componente de loading/carregamento
     * 
     * @return JLabel com indicador de carregamento
     */
    public static JLabel createLoadingLabel() {
        JLabel loadingLabel = new JLabel("⏳ Carregando...");
        loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loadingLabel.setVerticalAlignment(SwingConstants.CENTER);
        loadingLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        loadingLabel.setForeground(Color.GRAY);
        return loadingLabel;
    }
    
    /**
     * Cria um componente indicando que não há conteúdo
     * 
     * @return JLabel com mensagem de sem conteúdo
     */
    public static JLabel createNoContentLabel() {
        JLabel noContentLabel = new JLabel("📷 Nenhuma imagem disponível");
        noContentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        noContentLabel.setVerticalAlignment(SwingConstants.CENTER);
        noContentLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        noContentLabel.setForeground(Color.DARK_GRAY);
        return noContentLabel;
    }
}
