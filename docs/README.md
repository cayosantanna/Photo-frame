# PhotoFrame RMI – Guia do Projeto (atualizado)

Sistema distribuído (Java RMI) com uploader web embutido para uma galeria colaborativa de fotos e vídeos curtos. Suporta imagens (JPEG/PNG) e reprodução de vídeos MP4 no viewer (via JavaFX Media).

## Principais recursos
- Contrato RMI `br.com.photoframe.compartilhado.GaleriaRemota`:
  - Upload: `uploadFile(fileName, bytes, clientId)`
  - Slideshow: `getNextDisplayFile()`, `getNextDisplayFileByDate(date)`
  - Listas: `getFileList()`, `getFileListByClient(clientId)`
  - Integridade: `verifyFileIntegrity(fileName, md5)`
  - Gestão/Playback: `deleteFile(clientId, path)`, `setDisplayDateFilter(date)`, `getDisplayDateFilter()`, `setPaused`, `setPlaybackIntervalMillis`, `setForcedDisplayFile`, `setLoopVideo`, `setVideoPaused`, `next`, `previous`, `getPlaybackConfig()`
- Servidor `br.com.photoframe.servidor.ServidorGaleria` (+ HTTP em `ServidorHttpUploader`):
  - Armazena em `uploads/YYYY/MM/DD/` com nome `yyyyMMdd_HHmmss_nome.ext`.
  - Fila ordenada e troca garantida por intervalo no backend.
  - HTTP embutido (porta 18080) com rotas: `/uploader`, `/upload`, `/myfiles`, `/allfiles`, `/file`, `/delete`, `/control`, `/next`, `/previous`, `/health`.
- Uploader Web (mobile/desktop):
  - Multi-arquivos com barra de progresso.
  - “Meus Arquivos”: lista/exclusão dos seus envios (via clientId persistido no navegador – cookie/localStorage).
  - “Controles do Display”: pausa, intervalo (s), filtro de data (yyyy-MM-dd), forçar arquivo, loop de vídeo e “pausar vídeo”.
- Cliente Desktop (`br.com.photoframe.cliente.ClienteUploader`):
  - UI em PT‑BR; persiste apenas `clientId`.
  - Envia, lista “Meus arquivos”, exclui e ajusta controles do display.
- Viewer (`br.com.photoframe.servidor.display.Visualizador`):
  - Abre automaticamente junto do servidor (pode ser iniciado separadamente via perfil `viewer`).
  - Janela com fullscreen e overlay moderno; QR do uploader sempre visível no topo direito.
  - Centraliza imagens/vídeos com preserveRatio; reprodução de vídeos MP4 via JavaFX Media, respeitando loop e pausa.

## Requisitos
- Java 17+ e Maven 3.8+.
- LAN (pode funcionar offline); libere 1099/18080 no firewall do host do servidor.

## Build e testes
```cmd
mvn clean test package
```

## Execução (sem scripts)
- Porta HTTP padrão: 18080 (ajuste com `-DhttpPort=PORTA`).

Exemplos (Windows cmd ou Linux/macOS):
```cmd
mvn -P server exec:java -DhttpPort=18080
mvn -P client exec:java -Dexec.args="192.168.1.50"
mvn -P viewer exec:java -Dexec.args="192.168.1.50 10 --fullscreen"
```

Observações
- O servidor inicia o Viewer automaticamente. Se desejar, rode o Viewer de outra máquina com o perfil `viewer` apontando para o IP do servidor.
- Logs no console: prefixos [WEB] (requisições HTTP) e [CMD] (comandos aplicados no servidor) facilitam diagnóstico.

## Operação offline (sem internet)
- Funciona em rede local sem internet. O viewer tenta usar o IP LAN para gerar o QR da página do uploader.
- Requisitos: dispositivos na mesma LAN/Wi‑Fi e firewall liberando 1099/18080.

## Limitações conhecidas
- Codecs do JavaFX: priorize MP4 com H.264/AAC.
- Propriedade de arquivos baseada em `clientId` persistido.

## Endpoints web (resumo)
- `GET /uploader`: página de upload/gestão/controles.
- `POST /upload?filename=...&clientId=...` (body octet-stream): envia arquivo.
- `GET /myfiles?clientId=...`: lista arquivos do cliente.
- `GET /allfiles`: lista todos os arquivos (admin/debug).
- `GET /file?clientId=...&path=...`: miniaturas/bytes de arquivo (somente do dono).
- `POST /delete?clientId=...&path=...`: exclui arquivo do cliente.
- `GET /control`: obtém status de playback (paused, interval, date, forced, loop, videoPaused).
- `POST /control?...`: aplica controles (paused, interval, date, forced, loop, videoPaused).
- `POST /next` e `POST /previous`: navega manualmente.

Notas de UI do Viewer
- Teclas ESC/F/F11 alternam tela cheia; Espaço pausa/retoma.
- QR code permanece visível no canto superior direito para facilitar o acesso ao uploader via celular.
