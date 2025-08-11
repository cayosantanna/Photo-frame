# PhotoFrame – Relatório do Trabalho de SPD

## 1. Visão geral e motivação
- Propósito: Porta-retrato digital colaborativo para uso doméstico/pequenos eventos. Vários usuários enviam fotos e vídeos curtos a um servidor que organiza e exibe em slideshow.
- Público-alvo: Famílias em casa; pequenos encontros (aniversários, churrascos), laboratórios/salas de aula.
- Contexto e decisão: Em rede local de confiança, foco em simplicidade e transparência de acesso/localização, não em controles pesados (conta/moderação/criptografia ponta a ponta).

### 1.1 Público-alvo detalhado
- Famílias: pais/avós/filhos compartilhando momentos; TV da sala como display.
- Pequenos eventos: aniversários, churrascos, encontros de amigos; convidados enviam mídias pelo Wi‑Fi local.
- Educação: salas de aula/labs exibindo registros de atividades dos alunos em tempo real.
- Espaços residenciais compartilhados: salas de condomínio/coliving com uma TV comunitária.

### 1.2 Cenários de uso práticos
- Reunião de família: vários parentes enviam 2–10 fotos; slideshow troca a cada 10s; novas fotos entram automaticamente.
- Festa de aniversário: 2–3 dispositivos enviam ao mesmo tempo; servidor aceita concorrência; exibição contínua.
- Aula/mostra: grupos enviam imagens dos resultados; display apresenta em sequência.
- Mural do mês: família adiciona imagens ao longo da semana; slideshow sempre mostra as mais recentes.

## 2. Requisitos funcionais
- RF1 Upload de mídia por múltiplos clientes (concorrente) via RMI e via HTTP embutido.
- RF2 Organização por data/hora: uploads/YYYY/MM/DD/timestamp_nome.ext.
- RF3 Slideshow sequencial e contínuo (round-robin) com troca por intervalo garantida no backend.
- RF4 Visualização em um cliente “display” (desktop) full-window, com suporte a vídeo MP4 (JavaFX Media).
- RF5 Listagem de arquivos para diagnóstico (getFileList) e por cliente (getFileListByClient).
- RF6 Exclusão pelo dono (deleteFile) disponível no desktop e via página web.
- RF7 Integração do uploader web com gestão e controles do slideshow (pausa, intervalo, data, forçar arquivo, loop de vídeo, pausar vídeo) e navegação (próximo/anterior).
- RF8 Integridade: verifyFileIntegrity (MD5) para comparar hash esperado x arquivo salvo.
- RF9 Validação de conteúdo: permitir jpg/jpeg/png/mp4; rejeitar demais; limitar a 100MB por arquivo.

## 3. Requisitos não-funcionais
- RNF1 Simplicidade de implantação (Java + Maven). Sem BD.
- RNF2 Integridade básica (hash MD5 pós-upload), sem criptografia.
- RNF3 Validações leves (extensões permitidas: jpg/jpeg/png/mp4; limite 100MB).
- RNF4 Tolerância a falhas: tratamento de RemoteException; Registry resiliente.
- RNF5 Usabilidade: cliente com UI simples (JFileChooser, sem e-mail obrigatório, ID readonly com copiar/gerar novo), display sem distrações; web uploader amigável (multi-arquivos, progresso, gestão e controles).
- RNF6 Persistência: se o servidor reiniciar, reconstrói a fila lendo uploads/.
- RNF7 Desempenho-alvo: latência baixa em LAN, 3–10 clientes esporádicos sem impacto perceptível.

## 4. Transparências demonstradas
- Acesso: Mesmos métodos RMI para locais/remotos (upload/download/lista).
- Localização: Cliente usa nome lógico “GaleriaService”; não sabe paths físicos.
- Concorrência: Métodos sincronizados no servidor; fila consistente.
- Falhas (básico): try-catch e reconexão simples no cliente.

## 5. Arquitetura e componentes
- GaleriaRemota: contrato RMI (uploadFile, getNextDisplayFile, getNextDisplayFileByDate, verifyFileIntegrity, getFileList, getFileListByClient, deleteFile, setPaused, setPlaybackIntervalMillis, setDisplayDateFilter, getDisplayDateFilter, setForcedDisplayFile, setLoopVideo, setVideoPaused, getPlaybackConfig, next, previous).
- ServidorGaleria: implementa o contrato; salva mídias, mantém fila, exporta RMI; HTTP embutido com uploader e endpoints de gestão/controle (porta 18080). Inicia automaticamente o Visualizador.
- ClienteUploader: Swing (Nimbus), sem e-mail obrigatório, ID readonly com copiar/gerar novo; seleção de arquivo, envio assíncrono (SwingWorker), listar/excluir próprios arquivos, aplicar filtros e controles do display.
- Visualizador (`br.com.photoframe.servidor.display.Visualizador`): janela com fullscreen/overlay moderno (QR permanente no topo direito), timer periódico (~700ms), redimensionamento proporcional e centralização; suporte a vídeo MP4 (JavaFX Media) com loop/pausa de vídeo.

### 5.1 Justificativas técnicas (Por que Java RMI?)
- Transparência de acesso/localização: chamadas remotas parecem locais, alinhado ao objetivo didático de SD.
- Simplicidade: evita overhead de HTTP/REST/gRPC e (de)serializações extras para este caso.
- Adequado ao contexto: todos componentes em Java, LAN doméstica, porta 1099 sob controle.
- Alternativas: REST/gRPC (mais interoperabilidade, maior esforço para o mesmo escopo); WebSockets (útil se web/mobile fossem requisitos). RMI cumpre o objetivo com menor complexidade.

## 6. Decisões de projeto
- File system como armazenamento: reduz complexidade e atende o contexto.
- MD5 ao invés de criptografia/assinatura: integridade suficiente para LAN doméstica.
- Sem moderação/contas: escopo e prazo; pode ser citado como trabalho futuro.
- Extensões limitadas e tamanho máximo: mitigação simples contra mau uso acidental.

### 6.1 Limitações (o que NÃO precisa por ser doméstico)
- Moderação/curadoria automatizada e sistema de contas completo.
- Criptografia de mídia em repouso/transporte dentro da LAN confiável.
- Escalabilidade de nuvem/CDN, multi-região, HA/cluster.
- Transcodificação de vídeo e streaming adaptativo.

## 7. Execução
- Compilar e testar: `mvn clean test package`
- Executar servidor (inicia o Viewer): `mvn -Pserver -DhttpPort=18080 exec:java`
- Cliente desktop: `mvn -Pclient -Dexec.args="HOST" exec:java`
- Viewer opcional (em outra máquina): `mvn -Pviewer -Dexec.args="HOST 10 --fullscreen" exec:java`
- Porta HTTP padrão: 18080; RMI: 1099. Mensagem exibida se a porta já estiver em uso.

## 8. Plano de testes
- Conexão RMI, upload simples, integridade (MD5), slideshow, concorrência com 2-3 clientes, atualização em tempo real, falhas e recuperação.

### 8.1 Roteiro de demonstração
1) Inicie o servidor; confirme logs.
2) Inicie o viewer; deve exibir mensagem aguardando.
3) Envie imagens por 1–3 clientes; confira logs com MD5 e paths.
4) Observe o slideshow trocando a cada 10s; novas imagens entram na rotação.
5) Opcional: verifique integridade com verifyFileIntegrity.

## 9. Limitações e futuros
- Compatibilidade de codecs depende do JavaFX (priorize H.264/AAC em MP4).
- Operação offline: funciona em LAN sem internet, desde que portas 1099/18080 estejam liberadas e dispositivos na mesma rede.
- Autenticação simples (PIN) e filtro de conteúdo são possíveis incrementos.
- Melhorias visuais: tema escuro, transições (fade), fontes personalizadas; preview no cliente; relatórios simples de uso.

## 10. Conclusão
Projeto funcional e enxuto, enfatizando transparências e viabilidade prática para uso doméstico, pronto para demonstração e evolução incremental.
