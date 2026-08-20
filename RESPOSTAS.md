# RESPOSTAS.md - Central de Atendimento da Turma via gRPC

**Disciplina:** Laboratorio de Desenvolvimento de Aplicacoes Moveis e Distribuidas
**Unidade:** U1 - Introducao ao Desenvolvimento de Aplicacoes Distribuidas
**OFFSET utilizado:** 46 (portas: gRPC-Java=50097, gRPC-Python=50107)

---

## Parte A - Transparencias em Sistemas Distribuidos

### Tarefa 4.1 - Reflexao sobre o laboratorio anterior (TCP/UDP/Multicast/WebSocket)

**Pergunta 1 - O endereco do servidor esta escrito diretamente no codigo do cliente?**

- **TCP:** Sim. Em `ClienteTCP.java` e `cliente_tcp.py`, o host `"localhost"` e a porta `5046` estao hard-coded no codigo. Isso **prejudica** a transparencia de localizacao: se o servidor mudar de maquina, e necessario editar e recompilar/reiniciar o cliente.
- **UDP:** Idem. `HOST = "localhost"` e `PORTA = 5047` estao fixos no codigo do cliente.
- **Multicast:** O endereco do grupo (`230.0.0.1`) e a porta (`4492`) estao fixos. Porem, o mecanismo e diferente - o cliente nao precisa saber onde o servidor esta, apenas qual grupo ingressar. Isso da uma transparencia de localizacao *parcial*: o servidor pode mudar de IP, mas a porta e o grupo precisam ser conhecidos.
- **WebSocket:** O host `"localhost"` e a porta (`8934`) estao fixos no codigo do cliente Python. Igual ao TCP na pratica.

**Pergunta 2 - O cliente precisa montar uma string de texto manualmente?**

- **TCP:** Sim. O cliente digita texto puro (`"ola monitor"`, `"hora"`) e o servidor faz parsing dessa string com `equalsIgnoreCase()`. E ausencia quase total de transparencia de acesso: o contrato entre cliente e servidor e apenas uma convencao informal.
- **UDP:** Idem - texto puro enviado como bytes, sem esquema de serializacao.
- **Multicast:** O servidor envia strings de texto sem nenhum esquema formal; o cliente recebe bytes e decodifica. Mesma situacao.
- **WebSocket:** O mural trafega mensagens de texto livre. O cliente envia qualquer string e o servidor retransmite para todos. Tambem sem contrato formal.

Em todos os quatro casos, ha ausencia de transparencia de acesso - o programador precisa pensar em "montar" e "interpretar" mensagens de texto manualmente.

**Pergunta 3 - O que aconteceria se o servidor mudasse de maquina?**

Em todos os quatro casos, seria necessario alterar o codigo-fonte do cliente (o endereco/porta hard-coded). Nenhuma das solucoes sobreviveria a essa mudanca sem alterar o codigo. Comparando com gRPC (ver resposta da Parte A, Pergunta 3 abaixo), o framework tambem nao elimina magicamente o endereco hard-coded, mas o ponto de mudanca fica isolado em uma unica configuracao, e o resto do codigo continua identico pois a chamada parece um metodo local.

---

### Perguntas - Parte A

**Pergunta 1 - Qual transparencia e mais visivel para o programador que usa um servico remoto?**

A **transparencia de acesso** e a mais visivel para quem usa um servico remoto. Ela determina se o programador precisa "pensar em rede" (abrir socket, serializar dados, fazer parse da resposta) ou se pode simplesmente chamar uma funcao e receber um resultado como se fosse local. No laboratorio anterior, tinhamos zero transparencia de acesso - cada mensagem era uma string montada manualmente. Com gRPC, a chamada `stub.consultarHorario(pergunta)` parece identica a uma chamada de metodo comum em Java ou Python.

A transparencia de localizacao tambem e relevante, mas e consequencia da de acesso: se voce precisa montar o pacote manualmente, voce ja sabe que esta falando "com a rede", e por isso o endereco hard-coded chama atencao. Quando a chamada parece local, o endereco vira um detalhe de configuracao.

**Pergunta 2 - Transparencia total e sempre desejavel?**

Nao. Um exemplo concreto: imagine um metodo `buscarTodosRegistros()` que retorna 10 milhoes de linhas de um banco remoto. Se a transparencia de acesso for total (a chamada parece local), o programador pode inadverdidamente chama-lo em um loop ou sem paginacao, gerando timeouts, travamentos e consumo excessivo de rede - problemas que nao ocorreriam se a operacao fosse explicitamente remota (o programador pensaria duas vezes antes de chamar).

Outro exemplo: tratamento de falhas. Se uma chamada remota "parece" local, o programador pode esquecer de tratar `RpcException`, `TimeoutException` ou desconexoes - excecoes que nao existem em chamadas locais. Esconder demais a natureza remota da operacao pode levar a sistemas frageis que "funcionam no desenvolvimento" (tudo local) e falham em producao (rede real com latencia e falhas).

**Pergunta 3 - Comparando cliente TCP com cliente gRPC (respondido apos concluir Partes C e D)**

No **cliente TCP**, o equivalente a "montar a mensagem" era:
```java
saida.println(linha);           // enviar string de texto bruto
String resposta = entrada.readLine(); // receber e interpretar string
```
E para o comando `hora`, o servidor precisava fazer `equalsIgnoreCase("hora")` - um contrato informal que qualquer typo quebra silenciosamente.

No **cliente gRPC**, isso some completamente:
```java
PerguntaHorario pergunta = PerguntaHorario.newBuilder().setNomeAluno(nome).build();
RespostaHorario resposta = stub.consultarHorario(pergunta);
System.out.println(resposta.getMensagem());
```
Nao ha `send`, `receive`, nem parsing. O stub gerado automaticamente cuida de: serializar `PerguntaHorario` para protobuf binario, abrir conexao HTTP/2, enviar, aguardar resposta, desserializar `RespostaHorario`, e retornar o objeto tipado.

Isso se relaciona diretamente com a **transparencia de acesso**: o gRPC a fornece automaticamente a partir do contrato `.proto`, enquanto no TCP o programador e responsavel por toda a camada de "traducao" entre logica de negocio e protocolo de rede.

---

## Parte B - Protocol Buffers e o contrato do servico

**Pergunta 1 - Vantagem do contrato explicito no .proto vs. combinado "de boca"**

No laboratorio anterior, o "contrato" entre cliente e servidor era implicito: o servidor esperava a string `"hora"` e o cliente precisava saber disso por convencao. Se o servidor mudasse para aceitar `"horario"`, o cliente quebraria sem nenhum aviso em tempo de compilacao.

Com o `central.proto`, o contrato e:
- **Explicito e legivel:** qualquer desenvolvedor novo le o `.proto` e sabe exatamente quais operacoes existem e quais campos cada mensagem tem.
- **Verificado em tempo de compilacao:** se o cliente tentar acessar um campo inexistente (ex: `resposta.getNomeServidor()`), o compilador Java rejeita imediatamente.
- **Versionavel:** mudancas no `.proto` ficam registradas no Git, e e possivel manter compatibilidade retroativa com campos `reserved` ou numeracao correta.
- **Automaticamente sincronizado:** o mesmo arquivo gera codigo para Java e Python, eliminando divergencias entre implementacoes.

**Pergunta 2 - O mesmo .proto gerando codigo para Java e Python**

Isso demonstra o principal beneficio do Protocol Buffers como IDL (Interface Definition Language): a definicao do contrato e **neutra em relacao a linguagem**. Uma equipe de backend em Java e um time de frontend em Python podem trabalhar de forma independente, sabendo que seus codigos vao se comunicar corretamente porque ambos seguem o mesmo `.proto`.

Em sistemas distribuidos reais (microsservicos), isso e fundamental: um servico escrito em Go pode se comunicar com um cliente em Python e outro em Java, todos a partir do mesmo `.proto`, sem nenhum "adapter" manual.

**Pergunta 3 - Identificando operacoes nos stubs gerados**

No arquivo gerado `CentralAtendimentoGrpc.java` (em `target/generated-sources/`), as operacoes aparecem como metodos do stub. Por exemplo, na classe interna `CentralAtendimentoBlockingStub`, existe o metodo:

```java
public br.pucminas.labdamd.central.RespostaHorario consultarHorario(
    br.pucminas.labdamd.central.PerguntaHorario request)
```

E para o streaming:
```java
public java.util.Iterator<br.pucminas.labdamd.central.Aviso> acompanharAvisos(
    br.pucminas.labdamd.central.InscricaoAvisos request)
```

Em Python, no `central_pb2_grpc.py`, a classe `CentralAtendimentoStub` define os mesmos metodos no `__init__` via `grpc.experimental.channel_ready_future`. As operacoes `ConsultarHorario` e `AcompanharAvisos` aparecem como atributos do stub criados por `channel.unary_unary(...)` e `channel.unary_stream(...)` respectivamente.

---

## Parte C - RPC unario: ConsultarHorario

**Pergunta 1 - O que acontece "por baixo dos panos" em stub.consultarHorario(pergunta)?**

Pelo menos tres coisas acontecem de forma completamente transparente:

1. **Serializacao (marshalling):** O objeto `PerguntaHorario` e serializado para o formato binario do Protocol Buffers. Isso e muito mais compacto e eficiente que texto UTF-8 (o campo `nome_aluno = "Nicolas"` vira poucos bytes com prefixo de campo + tamanho + dados).

2. **Transporte via HTTP/2:** O stub abre (ou reutiliza) uma conexao HTTP/2 com o servidor na porta 50097. O frame HTTP/2 e enviado com cabecalhos gRPC (`content-type: application/grpc`, `grpc-status` etc.). HTTP/2 permite multiplexar varias chamadas na mesma conexao TCP - vantagem sobre o HTTP/1.1 do WebSocket.

3. **Desserializacao e retorno tipado:** A resposta chega como bytes protobuf; o stub desserializa para um objeto `RespostaHorario` Java/Python completamente tipado. O metodo retorna esse objeto, e o chamador pode acessar `.getMensagem()` ou `.getHorarioAtual()` sem nenhum parsing manual.

**Pergunta 2 - Comparacao com ClienteTCP: quem faz o trabalho de "montar" e "interpretar"?**

No **ClienteTCP**:
- "Montar a mensagem" = `saida.println(mensagem)` - o programador envia uma string crua.
- "Interpretar a resposta" = `entrada.readLine()` - o programador recebe uma string e decide o que fazer com ela.
- O servidor fazia `equalsIgnoreCase("hora")` para identificar o comando.

No **gRPC**, esse trabalho e feito pelo **stub gerado automaticamente** a partir do `.proto`. O programador so lida com objetos Java/Python tipados (`PerguntaHorario`, `RespostaHorario`) - o stub cuida de toda a serializacao, transporte e desserializacao.

**Pergunta 3 - O que acontece se chamar stub.consultarHorario com o servidor desligado?**

Testado em Python: ao chamar `stub.ConsultarHorario(...)` com o servidor desligado, o cliente recebe imediatamente uma excecao:
```
grpc._channel._InactiveRpcError: <_InactiveRpcError of RPC that terminated with:
    status = StatusCode.UNAVAILABLE
    details = "failed to connect to all addresses"
```

Em Java, a excecao equivalente e `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception`.

Diferentemente do cliente UDP (que ficava bloqueado em silencio), o gRPC tem **deteccao de falha imediata e com codigo de status estruturado** (`StatusCode.UNAVAILABLE`), facilitando o tratamento de erros na aplicacao. O framework fornece transparencia de falha parcial: detecta e reporta a falha, mas cabe ao programador decidir se vai retentar, usar fallback ou propagar o erro.

---

## Parte D - RPC com streaming: AcompanharAvisos

**Pergunta 1 - Como fazer varios clientes gRPC receberem os mesmos avisos simultaneamente?**

No estado atual, `acompanharAvisos` atende um cliente por vez - cada chamada e uma conexao independente que recebe seus proprios 5 avisos. Para que varios clientes recebessem os **mesmos avisos ao mesmo tempo** (comportamento similar ao Multicast), seria necessario:

1. **Manter um registro de observadores ativos** no servidor (ex: uma lista thread-safe de `StreamObserver<Aviso>` em Java).
2. **Ter uma thread/processo separado** gerando os avisos, que itere sobre todos os observadores registrados e chame `onNext()` em cada um.
3. **Gerenciar o ciclo de vida** das conexoes: remover observadores que se desconectarem (detectando via `onError` ou verificando se o contexto ainda esta ativo com `context.isCancelled()`).

Essa e essencialmente a logica de um servidor de broadcast - o gRPC nao a fornece automaticamente (ao contrario do Multicast, que usa a infraestrutura de rede para distribuicao). A diferenca fundamental: **Multicast delega a distribuicao para a rede; gRPC streaming delega para o codigo do servidor**.

**Pergunta 2 - Java (StreamObserver/onNext) vs. Python (yield): qual e mais natural?**

Ambos alcancam o mesmo resultado - envio progressivo de respostas - mas com abordagens diferentes:

- **Java com StreamObserver:** imperativo e explicito. Voce chama `observador.onNext(aviso)` para cada item e `observador.onCompleted()` ao terminar. E verboso mas deixa claro o controle de fluxo. Tambem permite chamar `onError()` em qualquer ponto.

- **Python com `yield`:** declarativo e conciso. A funcao geradora `yield central_pb2.Aviso(...)` e idiomatica em Python - qualquer Pythonista reconhece o padrao imediatamente. O framework gRPC converte automaticamente o gerador em chamadas `onNext`/`onCompleted` equivalentes.

Pessoalmente, o metodo Python com `yield` e **mais natural de entender** porque o corpo da funcao le-se como um loop normal que "produz" valores - sem precisar conhecer a API `StreamObserver`. O Java e mais explicito, o que pode ser uma vantagem em codigos complexos onde e preciso condicionar o `onCompleted` ou fazer tratamento de erro granular.

**Pergunta 3 - O que acontece se o cliente fechar a conexao no meio dos 5 avisos?**

Testado: ao fechar o terminal do cliente Python durante o streaming, o servidor continua tentando enviar os avisos restantes. Na proxima chamada a `yield` (que internamente chama `onNext`), o gRPC detecta que a conexao foi encerrada e lanca uma excecao `grpc.RpcError` no servidor.

Em Python, o contexto da chamada fica disponivel como `context` no metodo `AcompanharAvisos` - e possivel verificar `context.is_active()` antes de cada `yield` para parar o envio graciosamente quando o cliente desconectar:

```python
def AcompanharAvisos(self, request, context):
    for i in range(1, 6):
        if not context.is_active():
            break
        yield central_pb2.Aviso(numero=i, texto=f"Aviso #{i}...")
        time.sleep(2)
```

Em Java, o `StreamObserver.onNext()` lancaria uma excecao se o cliente tivesse fechado, que seria capturada pelo `catch (InterruptedException e)` e chamaria `observador.onError(e)`. Para uma implementacao robusta, o ideal e verificar `Context.current().isCancelled()` dentro do loop.

---

*Respostas elaboradas com base na execucao pratica dos codigos gRPC em Java e Python, e na comparacao direta com as implementacoes TCP/UDP/Multicast/WebSocket do roteiro anterior.*
