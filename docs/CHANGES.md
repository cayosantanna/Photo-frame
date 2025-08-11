# Mudanças e Limpezas – 10/08/2025

Este documento descreve as melhorias e ajustes aplicados ao projeto.

## Web Uploader e Gestão
- Adicionado painel "Meus Arquivos" na página `/uploader` para listar e excluir arquivos do próprio cliente.
- Adicionado painel "Controles do Display" para pausar/retomar, ajustar intervalo (s), aplicar filtro de data e forçar arquivo específico.
- Novos controles: loop de vídeo e "pausar vídeo"; botões de Próximo/Anterior (`/next` e `/previous`).
- Lista de arquivos do cliente passa a ser recarregada automaticamente ao término dos uploads.

## Servidor (GaleriaServer)
- Porta HTTP fixa padrão: 18080 (configurável com `-DhttpPort`). Mensagem clara se porta ocupada.
- Rotação por intervalo agora é garantida no backend: mantém a mesma imagem até o intervalo expirar, evitando trocas antecipadas por consultas frequentes do viewer.
- Ao exibir mídia "forçada", atualização do último item servido para consistência.
- Ao deletar um arquivo, limpeza automática de referências `forcedRelativePath` e `lastServedRel` se apontarem para o item removido.
- Endpoints ampliados: `/allfiles`, `/file`, `/next`, `/previous`, e `/health`. `/control` (GET) retorna paused, interval, date, forced, loop, videoPaused.
- Logs adicionados: prefixo [WEB] para cada requisição HTTP e [CMD] para comandos aplicados no servidor (RMI/controle).
- Servidor passou a iniciar automaticamente o Visualizador (display) após subir RMI/HTTP.

## Testes
- `GaleriaServerTest` convertido para JUnit 5, com casos para `normalizeDatePrefix` cobrindo formatos válidos e inválidos.

## Cliente Desktop (GaleriaClient)
- UI em PT-BR, sem e-mail obrigatório (apenas `clientId` persistido).
- Campo ID é somente leitura; botões “Copiar ID” e “Gerar novo”.
- Upload, listagem, exclusão e controles (pausa, intervalo, data, forçar, loop de vídeo, pausar vídeo) disponíveis.

## Viewer (DisplayViewer) e Vídeo
- Adicionadas dependências JavaFX (controls/media/swing) com perfis por SO para habilitar vídeo.
- Viewer reproduz MP4 via JavaFX Media, centralizado e com preserveRatio. Loop e pausa de vídeo sincronizados com o servidor.
- Alternância imagem/vídeo via CardLayout; evita reiniciar a reprodução quando o mesmo conteúdo é retornado (hash SHA‒256).
- Overlay modernizado com QR code permanente no topo direito; teclas ESC/F/F11 para fullscreen e Espaço para pausar/retomar.
- Polling padrão reduzido para ~700 ms para refletir rapidamente comandos web. Botões Próximo/Anterior aplicam imediatamente mesmo durante vídeos.

## Observações de qualidade
- Warnings de estilo (ex.: multicatch, campos "poderiam ser final") foram mantidos para evitar churn desnecessário; não afetam funcionamento.
- POM configurado com JUnit Jupiter e Surefire. Os testes são executáveis via `mvn test`.

## Como executar (resumo)
- Build e testes: `mvn clean test package`
- Servidor (inicia o Viewer automaticamente): `mvn -Pserver -DhttpPort=18080 exec:java`
- Viewer independente (opcional): `mvn -Pviewer -Dexec.args="HOST 10 --fullscreen" exec:java`
- Cliente: `mvn -Pclient -Dexec.args="HOST" exec:java`
