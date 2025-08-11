package br.com.photoframe.compartilhado;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Contrato RMI em português (mantém nomes de métodos estáveis para compatibilidade).
 */
public interface GaleriaRemota extends Remote {
    boolean uploadFile(String fileName, byte[] fileData, String clientId) throws RemoteException;
    byte[] getNextDisplayFile() throws RemoteException;
    byte[] getNextDisplayFileByDate(String date) throws RemoteException;
    boolean verifyFileIntegrity(String fileName, String hash) throws RemoteException;
    List<String> getFileList() throws RemoteException;
    List<String> getFileListByClient(String clientId) throws RemoteException;
    boolean deleteFile(String clientId, String relativePath) throws RemoteException;
    /**
     * Lê bytes do arquivo relativo se e somente se o cliente informado for o dono.
     * Retorna null quando o arquivo não existe, não é válido ou não pertence ao cliente.
     */
    byte[] readFileIfOwner(String clientId, String relativePath) throws RemoteException;
    void setDisplayDateFilter(String date) throws RemoteException;
    String getDisplayDateFilter() throws RemoteException;
    void setPaused(boolean paused) throws RemoteException;
    void setPlaybackIntervalMillis(int ms) throws RemoteException;
    void setForcedDisplayFile(String relativePath) throws RemoteException;
    void setLoopVideo(boolean loop) throws RemoteException;
    void setVideoPaused(boolean paused) throws RemoteException;
    void setMuted(boolean muted) throws RemoteException;
    PlaybackConfig getPlaybackConfig() throws RemoteException;
    void next() throws RemoteException;
    void previous() throws RemoteException;
}
