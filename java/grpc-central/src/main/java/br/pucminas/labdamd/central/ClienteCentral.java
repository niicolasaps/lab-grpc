package br.pucminas.labdamd.central;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Scanner;

public class ClienteCentral {
    // TODO: substitua pelo seu OFFSET pessoal - use o MESMO valor do servidor
    static final int OFFSET = 46;

    public static void main(String[] args) {
        int porta = 50051 + OFFSET;
        ManagedChannel canal = ManagedChannelBuilder.forAddress("localhost", porta)
                .usePlaintext()
                .build();
        try {
            CentralAtendimentoGrpc.CentralAtendimentoBlockingStub stub =
                    CentralAtendimentoGrpc.newBlockingStub(canal);

            Scanner teclado = new Scanner(System.in);
            System.out.print("Digite seu nome: ");
            String nome = teclado.nextLine();

            // Chamada unaria: parece uma chamada de metodo local, mas atravessa a rede
            PerguntaHorario pergunta = PerguntaHorario.newBuilder().setNomeAluno(nome).build();
            RespostaHorario resposta = stub.consultarHorario(pergunta);
            System.out.println("[gRPC] " + resposta.getMensagem());

            // Chamada com streaming: o servidor envia varios Avisos ao longo do tempo
            System.out.println("[gRPC] Inscrevendo-se para acompanhar avisos...");
            InscricaoAvisos inscricao = InscricaoAvisos.newBuilder().setNomeAluno(nome).build();
            java.util.Iterator<Aviso> avisos = stub.acompanharAvisos(inscricao);
            while (avisos.hasNext()) {
                Aviso aviso = avisos.next();
                System.out.println("[gRPC] Recebido: " + aviso.getTexto());
            }
        } finally {
            canal.shutdown();
        }
    }
}
