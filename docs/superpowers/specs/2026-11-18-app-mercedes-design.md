# App Mercedes própria — Design

Data: 2026-11-18 · Carro: Mercedes C300de (2021, híbrido plug-in) · Telemóvel: Android

## Objetivo e porquê
O Sergio quer uma app Android **sua**, independente da app Mercedes me, para controlar o carro no dia a dia: uma app simples, só com o que usa.

## Funcionalidades (objetivos)
1. **Portas** — trancar e destrancar (destrancar pede o PIN de segurança Mercedes).
2. **Localização** — onde está o carro, num mapa, com botão "Navegar até lá".
3. **Ar condicionado** — ligar/desligar a pré-climatização imediata.
4. **Bateria** — percentagem atual e autonomia elétrica (km).
5. **Carregamento** — estado (a carregar / ligado / desligado) e **hora prevista de fim da carga** (`endofchargetime`), com "faltam X h Y min".

## Fora de âmbito (por agora)
- Android Auto (fase 2, se quiser).
- Janelas, arranque remoto, agendamentos, notificações automáticas.
- iPhone.
- Servidor ou Home Assistant — a app fala diretamente com a Mercedes.

## Anti-objetivos
- Guardar a palavra-passe ou o PIN em texto simples.
- Destrancar o carro sem confirmação explícita.
- Fazer pedidos em excesso aos servidores Mercedes (risco de bloqueio da conta).

## Restrições
- A API é **não oficial** (a mesma da app Mercedes me). Pode deixar de funcionar sem aviso; a Mercedes já impôs limites de pedidos.
- Região: Europa (`*.emea-prod.mobilesdk.mercedes-benz.com`).
- Instalação por APK (fora da Play Store).

## Abordagens consideradas
| | Abordagem | Prós | Contras |
|---|---|---|---|
| A | Servidor Python (reaproveita mbapi2020) + app fina | Correções da comunidade aplicam-se diretamente | Precisa de máquina sempre ligada |
| **B** | **App Android nativa que fala diretamente com a Mercedes** | Nada mais para manter ligado | Login e protocolo reescritos em Kotlin; correções manuais |
| C | Web app só no browser | Simples | Impossível: a Mercedes bloqueia pedidos do browser |

**Escolhida: B** (escolha do Sergio).

## Como funciona (decisões técnicas — escolhas do Claude)
- **Linguagem/UI:** Kotlin + Jetpack Compose, um só ecrã principal com cartões (Bateria/Carga, Portas, Clima, Localização).
- **Referência:** o projeto open-source `ReneNulschDE/mbapi2020` (licença MIT), portado para Kotlin.
- **Login:** OAuth2 com PKCE contra o login Mercedes (email → palavra-passe → código → tokens), com renovação automática do token. Alternativa por código enviado por email (fluxo PIN) se o login direto falhar.
- **Estado do carro:** ligação WebSocket (`wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws`) com mensagens protobuf; os ficheiros `.proto` reconstroem-se a partir dos descritores incluídos no projeto de referência.
- **Comandos:** via o mesmo WebSocket — `DoorsLock`, `DoorsUnlock` (com PIN), pré-climatização ZEV `now` iniciar/parar.
- **Segurança:** tokens e credenciais em `EncryptedSharedPreferences` (Android Keystore); PIN pedido no momento de destrancar, nunca guardado; ação de destrancar com confirmação.
- **Mapa:** OpenStreetMap (osmdroid), sem chave de API; botão abre o Google Maps para navegar.
- **Atualização:** dados ao abrir a app e por puxar para atualizar; sem sondagem contínua em segundo plano.

## Decidido com o Sergio
- App independente, abordagem B, apenas telemóvel Android.
- As 5 funcionalidades acima.
- Conta Mercedes **dedicada** (outro email), com o carro partilhado a partir da conta principal — protege a conta principal se a Mercedes bloquear pedidos.

## Deixado ao construtor
- Estrutura interna do código, nomes, estilo visual (limpo, escuro, legível ao sol).
- Mensagens de erro em português.
