import grpc
import central_pb2
import central_pb2_grpc

# TODO: substitua pelo seu OFFSET pessoal - use o MESMO valor do servidor
OFFSET = 46
PORTA = 50061 + OFFSET

def main():
    canal = grpc.insecure_channel(f"localhost:{PORTA}")
    stub = central_pb2_grpc.CentralAtendimentoStub(canal)

    nome = input("Digite seu nome: ")

    # Chamada unaria: parece uma chamada de funcao local, mas atravessa a rede
    resposta = stub.ConsultarHorario(central_pb2.PerguntaHorario(nome_aluno=nome))
    print(f"[gRPC] {resposta.mensagem}")

    # Chamada com streaming: o servidor envia varios Avisos ao longo do tempo
    print("[gRPC] Inscrevendo-se para acompanhar avisos...")
    for aviso in stub.AcompanharAvisos(central_pb2.InscricaoAvisos(nome_aluno=nome)):
        print(f"[gRPC] Recebido: {aviso.texto}")

if __name__ == "__main__":
    main()
