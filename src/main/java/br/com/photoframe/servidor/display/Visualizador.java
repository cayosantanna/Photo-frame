package br.com.photoframe.servidor.display;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import br.com.photoframe.compartilhado.GaleriaRemota;
import br.com.photoframe.compartilhado.PlaybackConfig;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

public class Visualizador {
    private final JLabel imageLabel = new JLabel("Aguardando...", SwingConstants.CENTER);
    private final JFrame frame;
    private final JPanel overlay;
    private final JPanel qrOverlay;
    private final JLabel qrLabel;
    private final JLabel hintLabel;
    private boolean fullscreen = false;
    private final GraphicsDevice gd;
    private final Timer idleTimer;
    private final Cursor blankCursor;
    private final Cursor defaultCursor;
    private final JFXPanel videoPanel;
    private final JPanel centerPanel;
    private final CardLayout centerCards;
    private MediaPlayer currentPlayer;
    private File currentTempFile;
    private String lastHash;
    private GaleriaRemota remote;
    private javax.swing.Timer pollTimer;
    private final AtomicBoolean fetchBusy = new AtomicBoolean(false);
    // Controle de ESC (1x sai do fullscreen, 2x minimiza)
    private long lastEscAt = 0L;
    private int escCount = 0;

    public Visualizador() {
        frame = new JFrame("Visualizador");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().setLayout(new BorderLayout());
    frame.getContentPane().setBackground(Color.BLACK);

        centerCards = new CardLayout();
    centerPanel = new JPanel(centerCards);
    centerPanel.setBackground(Color.BLACK);
        videoPanel = new JFXPanel();
    imageLabel.setOpaque(true);
    imageLabel.setBackground(Color.BLACK);
    imageLabel.setForeground(new Color(200,200,200));
    centerPanel.add(imageLabel, "image");
        centerPanel.add(videoPanel, "video");
        frame.getContentPane().add(centerPanel, BorderLayout.CENTER);

        overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(true);
        overlay.setBackground(new Color(20,20,20,150)); // overlay escuro translúcido
        overlay.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12),
            javax.swing.BorderFactory.createLineBorder(new Color(255,255,255,40), 1, true)
        ));
        hintLabel = new JLabel("ESC/F/F11: tela cheia  •  Espaço: pausar/retomar");
        hintLabel.setForeground(new Color(240,240,240));
        hintLabel.setFont(hintLabel.getFont().deriveFont(hintLabel.getFont().getStyle(), hintLabel.getFont().getSize2D() + 1f));

        // QR code overlay permanente (sempre visível)
        qrOverlay = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        qrOverlay.setOpaque(false);
        qrLabel = new JLabel();
        // Fundo arredondado para o QR para melhor contraste
        qrLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6),
            javax.swing.BorderFactory.createLineBorder(new Color(0,0,0,40), 1, true)
        ));

    // Hint no centro do overlay temporário (mostrado só com atividade)
        overlay.add(hintLabel, BorderLayout.CENTER);
        frame.getContentPane().add(overlay, BorderLayout.SOUTH);
        
    // QR code (mostrado só com atividade) no canto superior direito
        qrOverlay.add(qrLabel);
        frame.getContentPane().add(qrOverlay, BorderLayout.NORTH);
    overlay.setVisible(false);
    qrOverlay.setVisible(false);

        Toolkit tk = Toolkit.getDefaultToolkit();
        BufferedImage bi = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        blankCursor = tk.createCustomCursor(bi, new Point(0,0), "blank");
        defaultCursor = Cursor.getDefaultCursor();

    var activity = new MouseAdapter(){
            @Override public void mouseMoved(MouseEvent e){ showOverlayAndCursor(); }
            @Override public void mouseDragged(MouseEvent e){ showOverlayAndCursor(); }
        };
        frame.addMouseMotionListener(activity);
    centerPanel.addMouseMotionListener(activity);
    videoPanel.addMouseMotionListener(activity);
        frame.addKeyListener(new KeyAdapter(){
            @Override public void keyPressed(KeyEvent e){
                showOverlayAndCursor();
                int k = e.getKeyCode();
                if (k==KeyEvent.VK_ESCAPE) {
                    long now = System.currentTimeMillis();
                    if (now - lastEscAt <= 600) escCount++; else escCount = 1;
                    lastEscAt = now;
                    if (escCount >= 2) { minimizeWindow(); escCount = 0; return; }
                    if (fullscreen) { toggleFullscreen(); return; }
                    // não faz nada no modo janela para um único ESC
                    return;
                }
        if (k==KeyEvent.VK_F || k==KeyEvent.VK_F11) toggleFullscreen();
                if (k==KeyEvent.VK_SPACE) togglePause();
            }
        });

    idleTimer = new Timer(5000, ev -> { hideOverlayAndCursor(); });
        idleTimer.setRepeats(false);

        frame.setSize(800,600);
        frame.setVisible(true);

        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    // Sempre tenta iniciar em fullscreen (usuário pode sair com ESC)
    if (!fullscreen) toggleFullscreen();
    }

    public void start(String host) throws Exception { start(host, 350); }

    public void start(String host, int delayMs) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, 1099);
        this.remote = (GaleriaRemota) registry.lookup("GaleriaService");
        String httpPort = System.getProperty("httpPort", "18080");
        String qrHost = host;
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            String lan = findLanIPv4();
            if (lan != null) qrHost = lan;
        }
        String uploaderUrl = "http://" + qrHost + ":" + httpPort + "/uploader";
        setQrOnOverlay(uploaderUrl);

        // Poll periódico (não bloquear EDT)
        pollTimer = new javax.swing.Timer(delayMs, e -> {
            if (fetchBusy.getAndSet(true)) return;
            new Thread(() -> {
                try {
                    // Sincroniza controles de vídeo (RMI em background)
                    try {
                        if (remote != null) {
                            PlaybackConfig cfg = remote.getPlaybackConfig();
                            if (cfg != null) applyVideoControls(cfg);
                        }
                    } catch (java.rmi.RemoteException ignore) {}

                    // Busca próximo arquivo para exibir
                    byte[] data = null;
                    try { if (remote != null) data = remote.getNextDisplayFile(); } catch (java.rmi.RemoteException ignore) {}
                    if (data != null) {
                        String hash = hashBytes(data);
                        if (hash == null || !hash.equals(lastHash)) {
                            try {
                                ByteArrayInputStream in = new ByteArrayInputStream(data);
                                BufferedImage img = ImageIO.read(in);
                                if (img != null) {
                                    javax.swing.SwingUtilities.invokeLater(() -> { showImage(img); lastHash = hash; });
                                    return;
                                }
                            } catch (java.io.IOException ignore) {}
                            // Provável vídeo: delega para JavaFX
                            playVideoFromBytes(data);
                            lastHash = hash;
                        }
                    }
                } finally {
                    fetchBusy.set(false);
                }
            }, "pf-poll").start();
        });
        pollTimer.start();

        // Busca imediata para reduzir sensação de atraso inicial (com 1 retry curto)
        new Thread(() -> {
            try {
                byte[] data = remote.getNextDisplayFile();
                if (data == null) {
                    try { Thread.sleep(300); } catch (InterruptedException ignore) {}
                    data = remote.getNextDisplayFile();
                }
                if (data != null) {
                    String hash = hashBytes(data);
                    try {
                        ByteArrayInputStream in = new ByteArrayInputStream(data);
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) { showImage(img); lastHash = hash; return; }
                    } catch (java.io.IOException ignore) {}
                    playVideoFromBytes(data);
                    lastHash = hash;
                }
            } catch (java.io.IOException ignore) {}
        }, "pf-initial-fetch").start();
    }

    private static String findLanIPv4() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException ignore) { }
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local instanceof Inet4Address) return local.getHostAddress();
        } catch (java.net.UnknownHostException ignore) { }
        return null;
    }

    private void showImage(BufferedImage img) {
        int lw = Math.max(1, imageLabel.getWidth());
        int lh = Math.max(1, imageLabel.getHeight());
        int iw = img.getWidth();
        int ih = img.getHeight();
        double scale = Math.min((double) lw / iw, (double) lh / ih);
        int nw = Math.max(1, (int) Math.round(iw * scale));
        int nh = Math.max(1, (int) Math.round(ih * scale));
        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
        imageLabel.setText("");
        stopVideoIfPlaying();
        centerCards.show(centerPanel, "image");
    }

    public static void main(String[] args) throws Exception {
    String host = args.length>0?args[0]:(findLanIPv4()!=null?findLanIPv4():"localhost");
    int seconds = 5; boolean wantFs=false;
        for (int i=1;i<args.length;i++){
            if ("--fullscreen".equalsIgnoreCase(args[i])) wantFs=true;
            else { try { seconds = Math.max(1, Integer.parseInt(args[i])); } catch (NumberFormatException ignore) {} }
        }
        Visualizador dv = new Visualizador();
    if (wantFs && !dv.fullscreen) dv.toggleFullscreen();
    // Usa o polling padrão otimizado (300ms)
    dv.start(host);
    }

    private void toggleFullscreen(){
        try {
            if (!fullscreen) {
                frame.dispose();
                frame.setUndecorated(true);
                gd.setFullScreenWindow(frame);
                frame.setVisible(true);
                fullscreen = true;
                showOverlayAndCursor();
            } else {
                gd.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setVisible(true);
                fullscreen = false;
                showOverlayAndCursor();
            }
        } catch (RuntimeException ignore) {}
    }

    private void minimizeWindow() {
        try {
            if (fullscreen) {
                // Sai do modo fullscreen antes de minimizar para evitar inconsistências da API
                gd.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setVisible(true);
                fullscreen = false;
            }
            frame.setState(JFrame.ICONIFIED);
        } catch (RuntimeException ignore) {}
    }

    private void showOverlayAndCursor(){
    overlay.setVisible(true);
    qrOverlay.setVisible(true);
    frame.getContentPane().setCursor(defaultCursor);
        idleTimer.restart();
    }

    private void hideOverlayAndCursor(){
    overlay.setVisible(false);
    qrOverlay.setVisible(false);
    frame.getContentPane().setCursor(fullscreen ? blankCursor : defaultCursor);
    }

    private void setQrOnOverlay(String url){
        try {
            ImageIcon icon = new ImageIcon(createQrImage(url, 140));
            qrLabel.setIcon(icon);
            qrLabel.setToolTipText(url);
            qrLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
            // QR code com fundo semi-transparente para melhor visibilidade
            qrLabel.setOpaque(true);
            qrLabel.setBackground(new Color(255, 255, 255, 200));
        } catch (com.google.zxing.WriterException e) {
            qrLabel.setText("QR indisponível");
            qrLabel.setOpaque(true);
            qrLabel.setBackground(new Color(255, 255, 255, 200));
        }
    }

    /**
     * Reproduz vídeo a partir dos bytes recebidos do servidor
     * Cria arquivo temporário e usa JavaFX MediaPlayer para reprodução
     * 
     * @param data bytes do arquivo de vídeo (MP4)
     */
    private void playVideoFromBytes(byte[] data) {
        // Para qualquer vídeo que esteja tocando antes de iniciar novo
        stopVideoIfPlaying();
        
        try {
            // Cria arquivo temporário .mp4 para o JavaFX conseguir reproduzir
            // JavaFX MediaPlayer precisa de um arquivo ou URL, não pode usar bytes diretamente
            File tmp = File.createTempFile("pf_", ".mp4");
            
            // Escreve os bytes recebidos no arquivo temporário
            try (FileOutputStream fos = new FileOutputStream(tmp)) { 
                fos.write(data); 
            }
            
            // Guarda referência do arquivo para deletar depois
            currentTempFile = tmp;
            
            // Converte caminho do arquivo para URI (formato que JavaFX entende)
            String uri = tmp.toURI().toString();
            
            // Executa no thread do JavaFX (obrigatório para componentes JavaFX)
            Platform.runLater(() -> {
                try {
                    // Cria objeto Media a partir da URI do arquivo
                    Media media = new Media(uri);
                    
                    // Cria player para reproduzir a mídia
                    MediaPlayer player = new MediaPlayer(media);
                    
                    // Cria visualizador que exibe o vídeo na tela
                    MediaView view = new MediaView(player);
                    
                    // Mantém proporção original do vídeo (não distorce)
                    view.setPreserveRatio(true);
                    
                    // Usa StackPane para centralizar vídeo quando há letterboxing
                    StackPane root = new StackPane(view);
                    
                    // Cria cena JavaFX com o painel root
                    // Fundo preto para evitar bordas cinzas no vídeo
                    Scene scene = new Scene(root, javafx.scene.paint.Color.BLACK);
                    root.setStyle("-fx-background-color: black;");
                    
                    // Faz vídeo se adaptar ao tamanho do painel, mantendo proporção
                    view.fitWidthProperty().bind(root.widthProperty());
                    view.fitHeightProperty().bind(root.heightProperty());
                    
                    // Coloca a cena no painel JavaFX que está integrado ao Swing
                    videoPanel.setScene(scene);
                    
                    // Aplica configurações de reprodução do servidor (loop/pausa)
                    final boolean[] loopFlag = new boolean[]{false};
                    try {
                        // Busca configurações atuais do servidor
                        PlaybackConfig cfg = remote != null ? remote.getPlaybackConfig() : null;
                        if (cfg != null) {
                            loopFlag[0] = cfg.loopVideo;
                            
                            // Define se vídeo deve repetir ou tocar uma vez só
                            player.setCycleCount(loopFlag[0] ? MediaPlayer.INDEFINITE : 1);
                            
                            // Inicia pausado ou reproduzindo conforme configuração
                            if (cfg.videoPaused) player.pause(); 
                            else player.play();
                            // Mudo
                            try { player.setMute(cfg.muted); } catch (RuntimeException ignore) {}
                        }
                    } catch (java.rmi.RemoteException ignore) {
                        // Se não conseguir acessar servidor, toca normalmente
                        player.play();
                    }
                    
                    // Define o que fazer quando vídeo terminar
                    player.setOnEndOfMedia(() -> {
                        // Se não está em loop, solicita próximo item do servidor
                        if (!loopFlag[0]) {
                            new Thread(() -> {
                                try { if (remote != null) remote.next(); } catch (java.rmi.RemoteException ignore) {}
                            }, "pf-next").start();
                        }
                        // Se está em loop, o MediaPlayer.INDEFINITE já cuida da repetição
                    });
                    
                    // Guarda referência do player para controles posteriores
                    currentPlayer = player;
                    
                    // Limpa imagem anterior do painel de imagens
                    imageLabel.setIcon(null);
                    imageLabel.setText("");
                    
                    // Alterna para mostrar painel de vídeo em vez do de imagem
                    centerCards.show(centerPanel, "video");
                    
                } catch (RuntimeException ex) {
                    // Se JavaFX falhar, limpa e mantém na imagem
                    System.err.println("Erro ao reproduzir vídeo: " + ex.getMessage());
                    currentPlayer = null;
                }
            });
        } catch (java.io.IOException ex) {
            // Erro ao criar arquivo temporário
            System.err.println("Erro ao criar arquivo temporário para vídeo: " + ex.getMessage());
        }
    }

    /**
     * Sincroniza controles de vídeo com configurações do servidor
     * Este método é chamado periodicamente para manter player local
     * alinhado com configurações remotas (pausa, loop, etc)
     */
    private void syncVideoControls() {
        // Só faz sentido sincronizar se há um vídeo tocando
        MediaPlayer p = currentPlayer;
        if (p == null) return;
        
        try {
            // Busca configurações atuais do servidor
            PlaybackConfig cfg = remote != null ? remote.getPlaybackConfig() : null;
            if (cfg == null) return;
            
            // Captura configurações para usar no thread do JavaFX
            final boolean loop = cfg.loopVideo;
            final boolean vpaused = cfg.videoPaused;
            final boolean muted = cfg.muted;
            
            // Executa alterações no thread do JavaFX (obrigatório)
            Platform.runLater(() -> {
                try {
                    // Define se vídeo repete infinitamente ou toca uma vez
                    p.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                    
                    // Atualiza comportamento quando vídeo termina
                    if (loop) {
                        // Em loop: não faz nada especial no fim (repete automaticamente)
                        p.setOnEndOfMedia(() -> {});
                    } else {
                        // Sem loop: solicita próximo item quando terminar
                        p.setOnEndOfMedia(() -> {
                            try { 
                                if (remote != null) remote.next(); 
                            } catch (java.rmi.RemoteException ignore) {}
                        });
                    }
                    
                    // Aplica estado de pausa/reprodução
                    if (vpaused) p.pause(); 
                    else p.play();
                    try { p.setMute(muted); } catch (RuntimeException ignore) {}
                    
                    // Video pausado/reproduzido via configuração do servidor
                    // (sem necessidade de atualizar interface de botões)
                    
                } catch (RuntimeException ignore) {
                    // Ignora erros do JavaFX (ex: player já foi destruído)
                }
            });
        } catch (java.rmi.RemoteException ignore) {
            // Ignora erros de comunicação com servidor
        }
    }

    // Aplica controles de vídeo sem bloquear RMI no EDT/FX
    private void applyVideoControls(PlaybackConfig cfg) {
        MediaPlayer p = currentPlayer;
        if (p == null || cfg == null) return;
        final boolean loop = cfg.loopVideo;
        final boolean vpaused = cfg.videoPaused;
        final boolean muted = cfg.muted;
        Platform.runLater(() -> {
            try {
                p.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                if (loop) {
                    p.setOnEndOfMedia(() -> {});
                } else {
                    p.setOnEndOfMedia(() -> {
                        new Thread(() -> { try { if (remote != null) remote.next(); } catch (java.rmi.RemoteException ignore) {} }, "pf-next").start();
                    });
                }
                if (vpaused) p.pause(); else p.play();
                try { p.setMute(muted); } catch (RuntimeException ignore) {}
            } catch (RuntimeException ignore) {}
        });
    }

    private void togglePause() {
        if (remote == null) return;
        try {
            PlaybackConfig cfg = remote.getPlaybackConfig();
            boolean willPause = !cfg.paused;
            remote.setPaused(willPause);
            MediaPlayer p = currentPlayer;
            if (p != null) {
                final boolean pause = willPause;
                Platform.runLater(() -> { try { if (pause) p.pause(); else p.play(); } catch (RuntimeException ignore) {} });
            }
        } catch (java.rmi.RemoteException ignore) {}
    }

    private static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ignore) {
            return null;
        }
    }

    private void stopVideoIfPlaying() {
        try {
            if (currentPlayer != null) {
                MediaPlayer p = currentPlayer;
                currentPlayer = null;
                Platform.runLater(() -> {
                    try { p.stop(); } catch (RuntimeException ignore) {}
                });
            }
        } catch (RuntimeException ignore) {}
        if (currentTempFile != null) {
            try { currentTempFile.delete(); } catch (RuntimeException ignore) {}
            currentTempFile = null;
        }
        try { Platform.runLater(() -> videoPanel.setScene(null)); } catch (RuntimeException ignore) {}
    }

    private static BufferedImage createQrImage(String text, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0,0,size,size);
        g.setColor(Color.BLACK);
        for (int y=0;y<size;y++) {
            for (int x=0;x<size;x++) {
                if (matrix.get(x,y)) g.fillRect(x,y,1,1);
            }
        }
        g.dispose();
        return img;
    }
}
