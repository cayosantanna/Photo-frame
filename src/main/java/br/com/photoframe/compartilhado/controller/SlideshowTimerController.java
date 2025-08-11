package br.com.photoframe.compartilhado.controller;

import java.awt.event.ActionListener;

import javax.swing.Timer;

/**
 * Controlador para gerenciar o timer do slideshow
 * Centraliza a lógica de temporização e controle de apresentação
 */
public class SlideshowTimerController {
    private Timer slideshowTimer;
    private int intervalSeconds;
    private boolean isRunning;
    private ActionListener timerAction;
    
    /**
     * Cria um novo controlador de timer
     * 
     * @param initialInterval intervalo inicial em segundos
     * @param action ação a ser executada a cada intervalo
     */
    public SlideshowTimerController(int initialInterval, ActionListener action) {
        this.intervalSeconds = initialInterval;
        this.timerAction = action;
        this.isRunning = false;
        
        createTimer();
    }
    
    /**
     * Cria o timer com o intervalo configurado
     */
    private void createTimer() {
        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }
        
        slideshowTimer = new Timer(intervalSeconds * 1000, timerAction);
        slideshowTimer.setRepeats(true);
    }
    
    /**
     * Inicia o slideshow
     */
    public void start() {
        if (slideshowTimer != null && !isRunning) {
            slideshowTimer.start();
            isRunning = true;
            System.out.println("Timer iniciado: " + intervalSeconds + " segundos");
        }
    }
    
    /**
     * Para o slideshow
     */
    public void stop() {
        if (slideshowTimer != null && isRunning) {
            slideshowTimer.stop();
            isRunning = false;
            System.out.println("Timer parado");
        }
    }
    
    /**
     * Reinicia o timer (para quando há interação do usuário)
     */
    public void restart() {
        if (isRunning) {
            stop();
            start();
        }
    }
    
    /**
     * Atualiza o intervalo do timer
     * 
     * @param newIntervalSeconds novo intervalo em segundos
     */
    public void updateInterval(int newIntervalSeconds) {
        boolean wasRunning = isRunning;
        
        if (wasRunning) {
            stop();
        }
        
        this.intervalSeconds = newIntervalSeconds;
        createTimer();
        
        if (wasRunning) {
            start();
        }
        
        System.out.println("Intervalo atualizado para: " + newIntervalSeconds + " segundos");
    }
    
    /**
     * Verifica se o timer está rodando
     * 
     * @return true se o timer está ativo
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Obtém o intervalo atual em segundos
     * 
     * @return intervalo em segundos
     */
    public int getIntervalSeconds() {
        return intervalSeconds;
    }
    
    /**
     * Obtém o timer interno (para casos especiais)
     * 
     * @return objeto Timer
     */
    public Timer getTimer() {
        return slideshowTimer;
    }
    
    /**
     * Libera os recursos do timer
     */
    public void dispose() {
        if (slideshowTimer != null) {
            slideshowTimer.stop();
            slideshowTimer = null;
        }
        isRunning = false;
        timerAction = null;
    }
    
    /**
     * Força uma execução única da ação do timer (sem afetar o timer principal)
     */
    public void triggerOnce() {
        if (timerAction != null) {
            timerAction.actionPerformed(null);
        }
    }
}
