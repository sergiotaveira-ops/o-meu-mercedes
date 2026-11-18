# O Meu Mercedes

App Android pessoal para controlar o Mercedes C300de: trancar e destrancar, localização, pré-climatização, bateria e fim da carga.

> Usa a API **não oficial** da app Mercedes me (com base no projeto open-source [mbapi2020](https://github.com/ReneNulschDE/mbapi2020), licença MIT). Pode deixar de funcionar se a Mercedes mudar alguma coisa.

## 1. Preparar a conta Mercedes (uma vez)
1. Cria uma conta Mercedes me nova, com outro email.
2. Na app Mercedes me da tua conta principal, adiciona essa conta como utilizador do carro.
3. Entra uma vez na app oficial com a conta nova e aceita os termos.

## 2. Descarregar o APK
1. No GitHub, abre o repositório → separador **Actions** → a execução mais recente com ✓ verde.
2. Em **Artifacts**, descarrega **app-debug** (é um .zip com o ficheiro `.apk` lá dentro).
3. No telemóvel, abre o `.apk` e permite "instalar apps desconhecidas" quando o Android pedir.

## 3. Usar
- Entra com o email e a palavra-passe da conta nova.
- Puxa o ecrã para baixo para atualizar.
- Para destrancar, a app pede sempre o PIN de segurança Mercedes, que nunca fica guardado.
- Menu ⋮ → **Terminar sessão** apaga a conta guardada no telemóvel.

## Para quem mexe no código
- Os valores que a Mercedes pode mudar (versões, user agents) estão todos em `app/src/main/java/pt/sergiotaveira/mercedes/api/MbConstants.kt`.
- Os `.proto` são gerados a partir do mbapi2020 com `python3 -I tools/extract_protos.py <mbapi2020>/custom_components/mbapi2020/proto app/src/main/proto`.
- Os testes correm com `./gradlew testDebugUnitTest`.
