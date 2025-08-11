# Arquitetura do Sistema – PhotoFrame (atualizado em 10/08/2025)

## 1) Arquitetura geral (visão e diagrama)

```
+----------------------+         lookup/rebind          +----------------------+
|  GaleriaClient (n)   |  --------------------------->  |   RMI Registry 1099  |
|  (upload GUI Swing)  |                                +----------------------+
+----------------------+                                         |
           |                                                     | (nome lógico)
           | RMI: uploadFile/getFileList/getFileListByClient     v
           |                                            +----------------------+
           |                                            |    GaleriaServer     |
           |                                            |  (UnicastRemoteObj)  |
           |                                            |  - Validação         |
           |                                            |  - Integridade (MD5) |
           |                                            |  - Fila (round-robin + intervalo backend)|
           |                                            |  - FileStore (FS)    |
           |                                            +----------+-----------+
           |                                                       |
           |                                                       v
           |                                            uploads/YYYY/MM/DD/
           |
           |                                                       ^
           |                        HTTP embutido (porta 18080): /uploader /upload /myfiles /allfiles /file /delete /control /next /previous /health
           |
+----------------------+                                         ^
|   DisplayViewer      | -------------- RMI: getNextDisplayFile --|
| (slideshow Swing)    |                                         |
+----------------------+                                         |
```

- O Registry (porta 1099) mantém o nome lógico `GaleriaService` para lookup dos clientes.
- O servidor salva mídia no filesystem e mantém uma fila em memória com ordenação por timestamp.
- O viewer busca periodicamente o próximo item e exibe imagens e vídeos MP4 (via JavaFX Media). O overlay mostra QR permanente com a URL do uploader.
- O servidor inicia automaticamente o viewer local; também é possível rodá-lo em outra máquina via perfil Maven `viewer`.

---

## 2) Componentes principais e responsabilidades

- RMI Registry (1099)
  - Serviço de nomes para localizar `GaleriaService`.
- GaleriaServer (processo único)
  - Implementa GaleriaRemote.
  - Validação de entrada (extensões e tamanho).
  - Integridade (MD5) – cálculo e verificação.
  - Armazenamento em `uploads/` com subpastas por data e nome com prefixo de timestamp.
  - Fila ordenada de mídias (round-robin) com troca por intervalo garantida no backend e entrega ao viewer.
  - HTTP embutido com uploader e endpoints de gestão/controles.
  - Reconstrução da fila ao iniciar (varredura do FS).
- GaleriaClient (múltiplas instâncias)
  - UI em PT-BR para selecionar e enviar arquivo; painel de “Meus arquivos” (listar/excluir) e controles (pausar, intervalo, data, forçar arquivo) em paridade com a web.
  - Comunicação RMI, feedback de status, tolerância básica a falhas.
  - Sem e-mail obrigatório; usa apenas `clientId` persistido (copiar/gerar novo).
- DisplayViewer
  - Janela de exibição (Swing + JavaFX), timer periódico, redimensionamento proporcional e centralização, overlay com QR e fullscreen.
  - Suporte a vídeo MP4 (JavaFX Media); respeita controles de loop e pausa de vídeo, avançando ao fim quando não em loop.
  - Recuperação simples após falhas (exibe mensagem e tenta novamente no próximo tick).

---

## 3) Fluxo de dados

Upload
1. Usuário seleciona arquivo no GaleriaClient.
2. Cliente lê bytes e chama `uploadFile(nome, bytes, clientId)` via RMI.
3. Servidor valida extensão/tamanho; calcula timestamp e diretórios `YYYY/MM/DD`.
4. Grava em disco com nome `yyyyMMdd_HHmmss_nome.ext`.
5. Adiciona caminho relativo à fila e (opcional) calcula MD5 para log.
6. Retorna sucesso/erro ao cliente.

Exibição (slideshow)
1. DisplayViewer, a cada N segundos, chama `getNextDisplayFile()`.
2. Servidor escolhe próximo item (índice circular), lê bytes do arquivo.
3. Viewer tenta decodificar como imagem; se falhar, trata como MP4 e reproduz via JavaFX Media.
4. Exibição acontece; se não houver itens, mostra mensagem de espera. Vídeos respeitam loop/pausa e podem avançar ao terminar.

Listagem/Integridade
- `getFileList()` retorna paths relativos em ordem.
- `verifyFileIntegrity(fileName, md5)` compara MD5 calculado com esperado.

---

## 4) Estrutura de classes (principal)

- `br.com.photoframe.compartilhado.GaleriaRemota` (interface RMI)
  - Contrato remoto do sistema.
- `br.com.photoframe.servidor.ServidorGaleria` (servidor)
  - `extends UnicastRemoteObject implements GaleriaRemote`.
  - Campos: `List<String> fileQueue`, `int currentFileIndex`, constantes de validação e pastas.
  - Métodos: `uploadFile`, `getNextDisplayFile`, `getFileList`, `verifyFileIntegrity` e utilitários (MD5, varredura FS, etc.).
- `br.com.photoframe.cliente.ClienteUploader` (cliente de upload)
  - `extends JFrame` – UI (Nimbus), JFileChooser, SwingWorker para envio.
  - Se conecta ao Registry e ao `GaleriaService`.
- `br.com.photoframe.servidor.display.Visualizador` (cliente de exibição)
  - Swing + JavaFX (Media); alterna imagem/vídeo via CardLayout; `javax.swing.Timer` para polling de `getNextDisplayFile` (~700ms).
  - Overlay moderno com QR fixo no topo direito; fullscreen (ESC/F/F11) e pausa (Espaço).

Observação: Para manter simplicidade, serviços auxiliares (validação e MD5) podem permanecer métodos no servidor. Em evolução, poderiam virar classes utilitárias dedicadas.

---

## 5) Interfaces RMI (contrato)

- `boolean uploadFile(String fileName, byte[] fileData, String clientId) throws RemoteException`
  - Entrada: nome, bytes, id do cliente.
  - Saída: sucesso/falha.
  - Erros: RemoteException (rede/RMI), validação (extensão/tamanho).
- `byte[] getNextDisplayFile() throws RemoteException`
- `byte[] getNextDisplayFileByDate(String date) throws RemoteException`
  - Saída: bytes do próximo item; `null` se não houver.
- `boolean verifyFileIntegrity(String fileName, String hash) throws RemoteException`
  - Verifica MD5 do arquivo armazenado vs hash esperado (hex).
- `List<String> getFileList() throws RemoteException`
  - Retorna caminhos relativos a `uploads/` em ordem.
- `List<String> getFileListByClient(String clientId) throws RemoteException`
- `boolean deleteFile(String clientId, String relativePath) throws RemoteException`
- `void setDisplayDateFilter(String date) / String getDisplayDateFilter()`
- `void setPaused(boolean) / void setPlaybackIntervalMillis(int) / void setForcedDisplayFile(String)`
- `void setLoopVideo(boolean) / void setVideoPaused(boolean) / PlaybackConfig getPlaybackConfig()`
- `void next() / void previous()`

---

## 6) Organização de arquivos (FS)

- Raiz: `uploads/`
- Subpastas por data: `uploads/YYYY/MM/DD/`
- Nome: `yyyyMMdd_HHmmss_nome.ext`
- Exemplo: `uploads/2025/08/10/20250810_153045_aniversario.jpg`
- Fila em memória usa caminhos relativos (`YYYY/MM/DD/arquivo.ext`).
- Na inicialização, o servidor varre `uploads/` para reconstruir a fila em ordem.
- O uploader web associa envios ao `clientId` persistido no navegador; o cliente desktop persiste `clientId` (sem e-mail).
- Índices persistentes: `.owners.tsv` (dono por arquivo) e `.hashes.tsv` (MD5 por arquivo) sob `uploads/`.

---

## 7) Concorrência

- Métodos do servidor são `synchronized` para manter consistência da fila e do índice.
- `uploadFile` serializa gravação e atualização da fila, suficiente para 3–10 clientes em LAN.
- `getNextDisplayFile` usa índice circular seguro no mesmo lock.
- Em caso de I/O falhar (arquivo removido), o servidor remove o item da fila e tenta o próximo.
- Evoluções possíveis (se exigido): locks mais granulares, `ReentrantReadWriteLock`, filas concorrentes, thread pool.

---

## 10) Execução e distribuição

- Porta HTTP fixa: 18080 (padrão; pode ser alterada com `-DhttpPort`). RMI: 1099.
- QR code: exibido no overlay do DisplayViewer (canto superior direito), apontando para `/uploader`.
- Logs: prefixos [WEB] para endpoints HTTP e [CMD] para comandos recebidos (RMI/controle) auxiliam o diagnóstico.
- Execução recomendada via perfis Maven: `server`, `client` e `viewer`.
- Pacote universal: `mvn clean package` gera `target/PhotoFrame-1.0-SNAPSHOT-dist.zip` com JAR e libs.
- Requisitos: Java 17+ e Maven 3.8+.

---

## 8) Transparências aplicadas

- Acesso: chamando métodos RMI, o cliente não distingue local/remoto.
- Localização: nome lógico `GaleriaService`; clientes não conhecem paths físicos.
- Concorrência: múltiplos clientes simultâneos; consistência preservada com sincronização simples.
- Falhas: tratamento de `RemoteException` e reconexão manual simples; servidor reconstrói fila ao iniciar.

---

## 9) Simplicidade e eficiência no contexto doméstico

- Zero banco de dados; apenas filesystem e timestamps para ordenação.
- Válido para LAN confiável: sem criptografia pesada; integridade via MD5 é suficiente.
- UI minimalista (cliente e display) para operação plug-and-play.
- Código enxuto, fácil de demonstrar e manter; pronto para incrementos como player de vídeo, tela cheia, transições e suporte a vídeo no viewer.
