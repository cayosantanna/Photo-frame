package br.com.photoframe.compartilhado;

import java.io.Serializable;

public class PlaybackConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    public boolean paused;
    public int intervalMillis = 10_000; // padrão 10s
    public String dateFilterPrefix; // yyyy/MM/dd/ ou null
    public String forcedRelativePath; // força exibir este arquivo específico (ou null)
    // Novos controles
    public boolean loopVideo;      // quando true, vídeos repetem em loop no viewer
    public boolean videoPaused;    // quando true, pausa apenas o player de vídeo (sem pausar slideshow)
    public boolean muted;          // quando true, reprodutor de vídeo fica sem som

    public PlaybackConfig copy() {
        PlaybackConfig c = new PlaybackConfig();
        c.paused = this.paused;
        c.intervalMillis = this.intervalMillis;
        c.dateFilterPrefix = this.dateFilterPrefix;
        c.forcedRelativePath = this.forcedRelativePath;
        c.loopVideo = this.loopVideo;
        c.videoPaused = this.videoPaused;
    c.muted = this.muted;
        return c;
    }
}
