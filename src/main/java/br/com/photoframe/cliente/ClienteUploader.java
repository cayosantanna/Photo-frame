package br.com.photoframe.cliente;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import br.com.photoframe.compartilhado.GaleriaRemota;
import br.com.photoframe.compartilhado.PlaybackConfig;

public class ClienteUploader extends JFrame {
    private transient GaleriaRemota stub;
        private final JTextField hostField = new JTextField("", 16);
    private final JTextField clientIdField = new JTextField("", 12);
    private static final String PREF_CLIENT_ID = "clientId";
    private static final String PREF_HOST = "host";
    private final Preferences prefs = Preferences.userNodeForPackage(ClienteUploader.class);
    private final JButton connectButton = new JButton("Conectar");
    private final JButton selectButton = new JButton("Selecionar Arquivo");
    private final JButton sendButton = new JButton("Enviar");
    private final JButton myListButton = new JButton("Listar meus envios");
    private final JButton deleteButton = new JButton("Excluir Selecionado");
    private final JButton showByDateButton = new JButton("Filtrar por data...");
    private final JButton copyIdButton = new JButton("Copiar ID");
    private final JButton newIdButton = new JButton("Gerar novo");
    private final javax.swing.JCheckBox pausedCheck = new javax.swing.JCheckBox("Pausar");
    private final javax.swing.JCheckBox loopVideoCheck = new javax.swing.JCheckBox("Loop vídeo");
    private final javax.swing.JCheckBox videoPausedCheck = new javax.swing.JCheckBox("Pausar vídeo");
    private final javax.swing.JCheckBox mutedCheck = new javax.swing.JCheckBox("Sem som");
    private final javax.swing.JSpinner intervalSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(10, 1, 600, 1));
    private final javax.swing.JTextField dateField = new javax.swing.JTextField(10);
    private final javax.swing.JComboBox<String> forcedCombo = new javax.swing.JComboBox<>(new javax.swing.DefaultComboBoxModel<>());
    private final JButton applyCtrlButton = new JButton("Aplicar");
    private final JButton prevButton = new JButton("◀ Anterior");
    private final JButton nextButton = new JButton("Próximo ▶");
    private final JLabel fileLabel = new JLabel("Nenhum arquivo selecionado");
    private final JLabel statusLabel = new JLabel("Status: aguardando conexão");
    private Path selectedFile;
    private final javax.swing.JList<String> myList = new javax.swing.JList<>(new javax.swing.DefaultListModel<>());

    public ClienteUploader(String initialHost) {
        super("PhotoFrame - Cliente de Upload");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 420);
        setLocationRelativeTo(null);

            if (initialHost == null || initialHost.isBlank()) {
                String saved = prefs.get(PREF_HOST, null);
                if (saved != null && !saved.isBlank()) {
                    initialHost = saved;
                } else {
                    String lan = tryFindLanIPv4();
                    initialHost = lan != null ? lan : "localhost";
                }
            }
            hostField.setText(initialHost);
        String savedId = prefs.get(PREF_CLIENT_ID, null);
        if (savedId == null || savedId.isBlank()) {
            savedId = "client-" + UUID.randomUUID().toString().substring(0, 8);
            prefs.put(PREF_CLIENT_ID, savedId);
        }
        clientIdField.setText(savedId);
        clientIdField.setEditable(false);
        sendButton.setEnabled(false);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Servidor:"));
        top.add(hostField);
        top.add(connectButton);
        top.add(new JLabel("Meu ID:"));
        top.add(clientIdField);
        top.add(copyIdButton);
        top.add(newIdButton);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(selectButton); row1.add(sendButton);
        row1.add(myListButton); row1.add(deleteButton); row1.add(showByDateButton);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileLabel.setPreferredSize(new Dimension(500, 20));
        row2.add(fileLabel);
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel.setPreferredSize(new Dimension(500, 20));
        row3.add(statusLabel);
        JPanel row4 = new JPanel(new BorderLayout());
        row4.add(new JLabel("Meus arquivos (selecione para excluir):"), BorderLayout.NORTH);
        row4.add(new javax.swing.JScrollPane(myList), BorderLayout.CENTER);
        JPanel ctrl = new JPanel();
        ctrl.setLayout(new BoxLayout(ctrl, BoxLayout.Y_AXIS));
        JPanel r5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r5.add(pausedCheck);
        r5.add(new JLabel("Intervalo (s):")); r5.add(intervalSpinner);
        JPanel r5b = new JPanel(new FlowLayout(FlowLayout.LEFT));
    r5b.add(loopVideoCheck); r5b.add(videoPausedCheck); r5b.add(mutedCheck);
        JPanel r6 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r6.add(new JLabel("Data (yyyy-MM-dd):")); dateField.setPreferredSize(new Dimension(110, 24)); r6.add(dateField);
        r6.add(new JLabel("Forçar arquivo:")); forcedCombo.setPreferredSize(new Dimension(280, 24)); r6.add(forcedCombo);
        JPanel r7 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r7.add(applyCtrlButton); r7.add(prevButton); r7.add(nextButton);
        ctrl.add(r5); ctrl.add(r5b); ctrl.add(r6); ctrl.add(r7);
        center.add(row1); center.add(row2); center.add(row3); center.add(row4);
        center.add(ctrl);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(center, BorderLayout.CENTER);

    connectButton.addActionListener(this::onConnect);
    hostField.addActionListener(ev -> onConnect(null));
        selectButton.addActionListener(this::onSelectFile);
        sendButton.addActionListener(this::onSend);
        myListButton.addActionListener(this::onLoadMyList);
        deleteButton.addActionListener(this::onDeleteSelected);
        showByDateButton.addActionListener(this::onShowByDate);
        copyIdButton.addActionListener(this::onCopyId);
        newIdButton.addActionListener(this::onGenerateNewId);
        applyCtrlButton.addActionListener(ev -> applyControls());
        prevButton.addActionListener(ev -> doPrevious());
        nextButton.addActionListener(ev -> doNext());

        SwingUtilities.invokeLater(() -> tryConnect(hostField.getText().trim()));
    }

    private void onConnect(ActionEvent e) {
        String h = hostField.getText().trim();
        prefs.put(PREF_HOST, h);
        tryConnect(h);
    }

    private void onSelectFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile().toPath();
            fileLabel.setText("Selecionado: " + selectedFile.toString());
            updateSendEnabled();
        }
    }

    private void onSend(ActionEvent e) {
        if (stub == null) { setStatus("Conecte-se primeiro."); return; }
        if (selectedFile == null) { setStatus("Nenhum arquivo selecionado."); return; }
        sendButton.setEnabled(false);
        selectButton.setEnabled(false);
        setStatus("Enviando...");
        new SwingWorker<Boolean, Void>() {
            private String message = "";
            @Override protected Boolean doInBackground() {
                try {
                    byte[] data = Files.readAllBytes(selectedFile);
                    String name = selectedFile.getFileName().toString();
                    boolean ok = stub.uploadFile(name, data, clientIdField.getText().trim());
                    message = ok ? "Upload concluído!" : "Não foi possível enviar.";
                    return ok;
                } catch (RemoteException ex) {
                    message = "Erro de comunicação: " + ex.getMessage();
                } catch (IOException ex) {
                    message = "Erro de IO: " + ex.getMessage();
                }
                return false;
            }
            @Override protected void done() {
                setStatus(message);
                selectButton.setEnabled(true);
                updateSendEnabled();
                JOptionPane.showMessageDialog(ClienteUploader.this, message,
                    message.startsWith("Upload concluído") ? "Sucesso" : "Aviso",
                    message.startsWith("Upload concluído") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            }
        }.execute();
    }

    private void onLoadMyList(ActionEvent e) {
        if (stub == null) { setStatus("Conecte-se primeiro."); return; }
        String cid = clientIdField.getText().trim();
        if (cid.isEmpty()) { setStatus("Informe seu ID."); return; }
        new SwingWorker<List<String>, Void>(){
            @Override protected List<String> doInBackground(){
                try { return stub.getFileListByClient(cid); } catch (RemoteException ex) { return java.util.Collections.emptyList(); }
            }
            @Override protected void done(){
                try {
                    List<String> items = get();
                    var model = (javax.swing.DefaultListModel<String>) myList.getModel();
                    model.clear();
                    for (String s: items) model.addElement(s);
                    setStatus(items.size()+" arquivo(s) listado(s).");
                } catch (java.util.concurrent.ExecutionException | InterruptedException ex) { setStatus("Não foi possível carregar a lista."); }
            }
        }.execute();
    }

    private void onDeleteSelected(ActionEvent e) {
        if (stub == null) { setStatus("Conecte-se primeiro."); return; }
        String sel = myList.getSelectedValue(); if (sel == null) { setStatus("Selecione um item para excluir."); return; }
        String cid = clientIdField.getText().trim(); if (cid.isEmpty()) { setStatus("Informe seu ID."); return; }
        int confirm = JOptionPane.showConfirmDialog(this, "Excluir definitivamente?\n"+sel, "Confirmar", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        new SwingWorker<Boolean, Void>(){
            @Override protected Boolean doInBackground(){ try { return stub.deleteFile(cid, sel); } catch (RemoteException ex){ return false; } }
            @Override protected void done(){ try { boolean ok = get(); setStatus(ok?"Excluído.":"Não foi possível excluir."); if (ok) onLoadMyList(null);} catch(java.util.concurrent.ExecutionException | InterruptedException ex){ setStatus("Erro ao excluir."); } }
        }.execute();
    }

    private void onShowByDate(ActionEvent e) {
        if (stub == null) { setStatus("Conecte-se primeiro."); return; }
        String date = JOptionPane.showInputDialog(this, "Data (yyyyMMdd, yyyy-MM-dd ou yyyy/MM/dd)\n(Deixe vazio para limpar o filtro):", "" );
        if (date == null) return;
        final String d = date.trim();
        new SwingWorker<Boolean, Void>(){
            @Override protected Boolean doInBackground(){ try { stub.setDisplayDateFilter(d.isEmpty()?null:d); return true; } catch (RemoteException ex){ return false; } }
            @Override protected void done(){ try { boolean ok = get(); setStatus(ok?(d.isEmpty()?"Filtro limpo.":"Filtro aplicado: "+d):"Não foi possível aplicar o filtro."); } catch(java.util.concurrent.ExecutionException | InterruptedException ex){ setStatus("Erro ao aplicar filtro."); } }
        }.execute();
    }

    private void tryConnect(String host) {
        if (host == null || host.isEmpty()) {
            String lan = tryFindLanIPv4();
            host = lan != null ? lan : "localhost";
        }
        final String target = host;
        setStatus("Conectando a " + target + "...");
        connectButton.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            private String message = "";
            @Override protected Boolean doInBackground() {
                try {
                    Registry registry = LocateRegistry.getRegistry(target, 1099);
                    stub = (GaleriaRemota) registry.lookup("GaleriaService");
                    message = "Conectado a " + target;
                    return true;
                } catch (RemoteException | NotBoundException ex) {
                    stub = null;
                    message = "Não foi possível conectar: " + ex.getMessage();
                    return false;
                }
            }
            @Override protected void done() {
                setStatus(message);
                connectButton.setEnabled(true);
                updateSendEnabled();
                if (stub != null) { syncControlsFromServer(); loadAllFiles(); }
            }
        }.execute();
    }

    private void setStatus(String text) { statusLabel.setText("Status: " + text); }
    private void updateSendEnabled() { sendButton.setEnabled(stub != null && selectedFile != null); }

    private void onCopyId(ActionEvent e){
        String id = clientIdField.getText().trim();
        if (id.isEmpty()) { setStatus("ID vazio"); return; }
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(id), null);
            setStatus("ID copiado para a área de transferência"); } catch (Exception ex) { setStatus("Falha ao copiar ID"); }
    }

    private void onGenerateNewId(ActionEvent e){
        int confirm = JOptionPane.showConfirmDialog(this,
            "Gerar um novo ID? Você perderá o vínculo com os envios atuais.",
            "Gerar novo ID", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        String newId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        prefs.put(PREF_CLIENT_ID, newId);
        clientIdField.setText(newId);
        myList.clearSelection();
        var model = (javax.swing.DefaultListModel<String>) myList.getModel();
        model.clear();
        if (stub != null) onLoadMyList(null);
        setStatus("Novo ID gerado");
    }

    public static void main(String[] args) {
        try { javax.swing.UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception ignore) {}
        String initialHost = args.length > 0 ? args[0] : null;
        if (initialHost == null || initialHost.isBlank()) {
            String lan = tryFindLanIPv4();
            initialHost = lan != null ? lan : "localhost";
        }
        final String hostInit = initialHost;
        SwingUtilities.invokeLater(() -> new ClienteUploader(hostInit).setVisible(true));
    }

    private static String tryFindLanIPv4() {
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

    private void syncControlsFromServer() {
        if (stub == null) return;
        new SwingWorker<Void, Void>(){
            PlaybackConfig cfg; String d;
            @Override protected Void doInBackground(){
                try { cfg = stub.getPlaybackConfig(); d = stub.getDisplayDateFilter(); } catch (RemoteException ex) { }
                return null;
            }
            @Override protected void done(){
                if (cfg != null) {
                    pausedCheck.setSelected(cfg.paused);
                    int sec = Math.max(1, cfg.intervalMillis/1000); intervalSpinner.setValue(sec);
                    loopVideoCheck.setSelected(cfg.loopVideo);
                    videoPausedCheck.setSelected(cfg.videoPaused);
                    mutedCheck.setSelected(cfg.muted);
                    if (cfg.forcedRelativePath != null && !cfg.forcedRelativePath.isBlank()) {
                        forcedCombo.setSelectedItem(cfg.forcedRelativePath);
                    } else {
                        forcedCombo.setSelectedItem(null);
                    }
                }
                dateField.setText(d==null?"":d);
            }
        }.execute();
    }

    private void loadAllFiles() {
        if (stub == null) return;
        new SwingWorker<java.util.List<String>, Void>(){
            @Override protected java.util.List<String> doInBackground(){ try { return stub.getFileList(); } catch(RemoteException ex){ return java.util.Collections.emptyList(); } }
            @Override protected void done(){
                try {
                    java.util.List<String> all = get();
                    var model = (javax.swing.DefaultComboBoxModel<String>) forcedCombo.getModel();
                    model.removeAllElements();
                    model.addElement("");
                    for (String s: all) model.addElement(s);
                } catch (java.util.concurrent.ExecutionException | InterruptedException ex) { }
            }
        }.execute();
    }

    private void applyControls() {
        if (stub == null) { setStatus("Conecte-se primeiro."); return; }
        new SwingWorker<Boolean, Void>(){
            @Override protected Boolean doInBackground(){
                try {
                    stub.setPaused(pausedCheck.isSelected());
                    int sec = (Integer) intervalSpinner.getValue();
                    stub.setPlaybackIntervalMillis(sec*1000);
                    stub.setLoopVideo(loopVideoCheck.isSelected());
                    stub.setVideoPaused(videoPausedCheck.isSelected());
                    stub.setMuted(mutedCheck.isSelected());
                    String d = dateField.getText().trim();
                    stub.setDisplayDateFilter(d.isEmpty()?null:d);
                    String forced = (String) forcedCombo.getSelectedItem();
                    if (forced != null && forced.isBlank()) forced = null;
                    stub.setForcedDisplayFile(forced);
                    return true;
                } catch (RemoteException ex) { return false; }
            }
            @Override protected void done(){ try { boolean ok = get(); setStatus(ok?"Controles aplicados.":"Não foi possível aplicar controles."); } catch(Exception ex){ setStatus("Erro ao aplicar controles."); } }
        }.execute();
    }

    private void doNext(){ if (stub==null){ setStatus("Conecte-se primeiro."); return; } new SwingWorker<Void,Void>(){ @Override protected Void doInBackground(){ try { stub.next(); } catch(RemoteException ex){} return null; } }.execute(); }
    private void doPrevious(){ if (stub==null){ setStatus("Conecte-se primeiro."); return; } new SwingWorker<Void,Void>(){ @Override protected Void doInBackground(){ try { stub.previous(); } catch(RemoteException ex){} return null; } }.execute(); }
}
