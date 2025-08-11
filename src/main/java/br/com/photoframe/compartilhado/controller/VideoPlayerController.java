package br.com.photoframe.compartilhado.controller;

import java.awt.BorderLayout;
import java.awt.Container;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

/**
 * Controlador para reprodução de vídeos usando JavaFX
 * Gerencia a interface de controle e a reprodução de mídia
 */
public class VideoPlayerController {
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private JFXPanel jfxPanel;
    private Button playPauseButton;
    private boolean isPlaying = false;
    private File tempVideoFile;
    
    public VideoPlayerController() {
        setupJavaFX();
    }
    
    /**
     * Configura o painel JavaFX para reprodução de vídeo
     */
    private void setupJavaFX() {
        jfxPanel = new JFXPanel();
        
        Platform.runLater(() -> {
            // Criar controles de vídeo
            playPauseButton = new Button("⏸️ Pausar");
            playPauseButton.setStyle("-fx-font-size: 14px; -fx-padding: 5px 15px;");
            playPauseButton.setOnAction(e -> togglePlayPause());
            
            // Layout dos controles
            VBox controls = new VBox(10);
            controls.getChildren().add(playPauseButton);
            controls.setStyle("-fx-alignment: center; -fx-padding: 10px;");
            
            // Container principal
            StackPane root = new StackPane();
            root.getChildren().add(controls);
            
            Scene scene = new Scene(root, 400, 100);
            jfxPanel.setScene(scene);
        });
    }
    
    /**
     * Reproduz vídeo a partir de bytes
     * 
     * @param videoBytes dados do vídeo
     * @param parentContainer container onde será exibido o painel de controle
     * @return true se iniciou reprodução com sucesso
     */
    public boolean playVideo(byte[] videoBytes, Container parentContainer) {
        if (videoBytes == null || videoBytes.length == 0) {
            return false;
        }
        
        try {
            // Limpar reprodução anterior
            stopVideo();
            
            // Criar arquivo temporário para o vídeo
            tempVideoFile = File.createTempFile("video_", ".mp4");
            tempVideoFile.deleteOnExit();
            
            try (FileOutputStream fos = new FileOutputStream(tempVideoFile)) {
                fos.write(videoBytes);
            }
            
            // Configurar reprodução no JavaFX
            Platform.runLater(() -> {
                try {
                    Media media = new Media(tempVideoFile.toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaView = new MediaView(mediaPlayer);
                    
                    // Configurar propriedades do player
                    mediaPlayer.setAutoPlay(true);
                    mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Loop infinito
                    
                    // Atualizar controles quando o status mudar
                    mediaPlayer.statusProperty().addListener((observable, oldValue, newValue) -> {
                        updateControlsStatus();
                    });
                    
                    // Atualizar a interface
                    updateControlsStatus();
                    
                } catch (Exception e) {
                    System.err.println("Erro ao configurar reprodução de vídeo: " + e.getMessage());
                }
            });
            
            // Adicionar painel de controles ao container
            if (parentContainer != null) {
                parentContainer.add(jfxPanel, BorderLayout.SOUTH);
                parentContainer.revalidate();
                parentContainer.repaint();
            }
            
            return true;
            
        } catch (IOException e) {
            System.err.println("Erro ao criar arquivo temporário para vídeo: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Alterna entre reproduzir e pausar
     */
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (isPlaying) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }
    
    /**
     * Atualiza o status dos controles baseado no estado do player
     */
    private void updateControlsStatus() {
        if (mediaPlayer == null || playPauseButton == null) return;
        
        Platform.runLater(() -> {
            MediaPlayer.Status status = mediaPlayer.getStatus();
            isPlaying = (status == MediaPlayer.Status.PLAYING);
            
            if (isPlaying) {
                playPauseButton.setText("⏸️ Pausar");
            } else {
                playPauseButton.setText("▶️ Reproduzir");
            }
        });
    }
    
    /**
     * Para a reprodução e limpa recursos
     */
    public void stopVideo() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            
            if (mediaView != null) {
                mediaView = null;
            }
            
            // Resetar controles
            isPlaying = false;
            if (playPauseButton != null) {
                playPauseButton.setText("▶️ Reproduzir");
            }
        });
        
        // Limpar arquivo temporário
        if (tempVideoFile != null && tempVideoFile.exists()) {
            try {
                Files.deleteIfExists(tempVideoFile.toPath());
            } catch (IOException e) {
                System.err.println("Erro ao deletar arquivo temporário: " + e.getMessage());
            }
            tempVideoFile = null;
        }
    }
    
    /**
     * Verifica se há vídeo sendo reproduzido
     */
    public boolean isVideoPlaying() {
        return mediaPlayer != null && isPlaying;
    }
    
    /**
     * Obtém o painel JavaFX para incorporar na interface Swing
     */
    public JFXPanel getJFXPanel() {
        return jfxPanel;
    }
    
    /**
     * Libera todos os recursos
     */
    public void dispose() {
        stopVideo();
        jfxPanel = null;
    }
}
