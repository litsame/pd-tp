import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Cliente {
    public static final int MAX_SIZE = 1024;
    public static final String ADDR_PORT_REQUEST = "GET_ADDR_PORT_TCP";
    public static final String SERVER_REQUEST = "SERVER_REQUEST";
    private static Socket socket = null;
    private static Request request = new Request();
    private static DatagramSocket SocketGRDS = null;
    private static InetAddress AddrGRDS, serverAddress = null;
    private static int PortGRDS, serverPort;
    private static ObjectInputStream oin, oinS;
    private static ObjectOutputStream oout;

    public static void main(String args[]) {
        DatagramPacket packet;
        Servidor server;
        ByteArrayInputStream bin;
        boolean connected = false;
        String ans;

        //Verifica se recebeu os argumentos necessários: endereço IP e porto de escuta do GRDS
        if (args.length != 2) {
            System.out.println("Sintaxe: java Cliente serverAddress serverUdpPort");
            return;
        }

        //Tenta contactar o GRDS para receber os dados de um servidor ativo
        try {

            //Cria um DatagramSocket e guarda o IP e porto de escuta do GRDS
            SocketGRDS = new DatagramSocket();
            AddrGRDS = InetAddress.getByName(args[0]);
            PortGRDS = Integer.parseInt(args[1]);
            SocketGRDS.setSoTimeout(5000);

            //Cria um DatagramPacket e envia-o ao GRDS através do DatagramSocket criado anteriormente
            packet = new DatagramPacket(ADDR_PORT_REQUEST.getBytes(), ADDR_PORT_REQUEST.length(), AddrGRDS, PortGRDS);
            SocketGRDS.send(packet);

            //Limpa o packet e recebe resposta do GRDS
            packet.setData(new byte[MAX_SIZE], 0, MAX_SIZE);
            SocketGRDS.receive(packet);

            //Lê o packet com os dados de um servidor ativo e cria um objeto
            bin = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            oin = new ObjectInputStream(bin);

            //Lê o objeto e guarda os dados
            server = (Servidor) oin.readObject();

            System.out.println("\nEndereço IP: " + server.getServerAddress().toString());
            System.out.println("Porto de escuta: " + server.getListeningPort());

        } catch (UnknownHostException e) {
            System.out.println("\nDestino desconhecido:\n\t" + e);
            return;
        } catch (NumberFormatException e) {
            System.out.println("\nO porto de escuta do servidor deve ser um inteiro positivo:\n\t" + e);
            return;
        } catch (SocketTimeoutException e) {
            System.out.println("\nNão foi recebida qualquer resposta do GRDS:\n\t" + e);
            return;
        } catch (SocketException e) {
            System.out.println("\nOcorreu um erro ao nível do socket:\n\t" + e);
            return;
        } catch (IOException e) {
            System.out.println("\nOcorreu um erro no acesso ao socket:\n\t" + e);
            return;
        } catch(NullPointerException e) {
            System.out.println("\nNão foi encontrado um servidor ativo:\n\t" + e);
            return;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        Scanner sc = new Scanner(System.in);
        ArrayList<String> credentials = new ArrayList<>(3);
        int option = 0;

        //Tenta conectar-se ao servidor
        try {

            serverAddress = server.getServerAddress();
            serverPort = server.getListeningPort();

            //Cria um Socket para comuninar com o servidor
            socket = new Socket(serverAddress, serverPort);

            oout = new ObjectOutputStream(socket.getOutputStream());
            oinS = new ObjectInputStream(socket.getInputStream());

            //Envia pedido ao servidor
            request.setMessage(SERVER_REQUEST);
            oout.writeUnshared(request);

            request = (Request) oinS.readObject();

            System.out.println("\n" + request.getMessage() + "\n");

//            Runnable r = new Cliente();
//            new Thread(r).start();

            while (true) {


                if (!request.isSession()) {
                    System.out.println("1 - Iniciar sessão");
                    System.out.println("2 - Criar conta");
                }

                System.out.println("3 - Sair");
                System.out.print("\nOpção: ");
                while (!sc.hasNextInt());
                option = sc.nextInt();

                if (option == 1 && !request.isSession()) {
                    request.setMessage("LOGIN");
                    getUserCredentials(credentials, request.getMessage());
                    request.setUsername(credentials.get(0));
                    request.setPassword(credentials.get(1));

                    //Tenta enviar pedido de LOGIN ao servidor
                    try {
                        oout.writeUnshared(request);

                    } catch (SocketException e) {
                        System.out.println("\nLigação com o servidor perdida.\nA procurar novo servidor...");
                        try {
                            Thread.sleep(2000);

                            if (getNewServer()) continue;
                            else {
                                System.out.println("\nNão foi possível encontrar um novo servidor/o GRDS fechou.\nA fechar o cliente...\n");
                                Thread.sleep(2000);
                                return;
                            }
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                        }
                    }

                    request = (Request) oinS.readObject();
                    System.out.println("\n" + request.getMessage() + "\n");

                    if (request.getMessage().equals("SUCCESS")) {
                        request.setSession(true);
                    }
                    else if (request.getMessage().equals("SERVER_OFF")){
                        getNewServer();
                    }
                } else if (option == 2 && !request.isSession()) {
                    request.setMessage("CREATE_ACCOUNT");
                    getUserCredentials(credentials, request.getMessage());
                    request.setUsername(credentials.get(0));
                    request.setPassword(credentials.get(1));
                    request.setName(credentials.get(2));

                    //Tenta enviar pedido de CREATE_ACCOUNT ao servidor
                    try {
                        oout.writeUnshared(request);

                    } catch (SocketException e) {
                        System.out.println("\nLigação com o servidor perdida.\nA procurar novo servidor...");

                        try {
                            Thread.sleep(2000);

                            if (getNewServer()) continue;
                            else {
                                System.out.println("\nNão foi possível encontrar um novo servidor/o GRDS fechou.\nA fechar cliente...\n");
                                Thread.sleep(2000);
                                return;
                            }
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                        }
                    }

                    request = (Request) oinS.readObject();
                    System.out.println("\n" + request.getMessage() + "\n");

                    if (request.getMessage().equals(null)) {
                        System.out.println("\nErro ao tentar criar conta.\n");
                    }
                    else if (request.getMessage().equals("SERVER_OFF")){
                        getNewServer();
                    }
                    else if (request.getMessage().equals("SUCCESS")) {
                        request.setSession(true);
                    }
                } else if (option == 3) {
                    SocketGRDS.close();
                    socket.close();
                    return;
                }
                else {
                    System.out.println("\nOpção inválida.\n");
                }
            }
        } catch (SocketTimeoutException e) {

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void getUserCredentials(ArrayList<String> cred, String message) {
        Scanner sc = new Scanner(System.in);

        System.out.print("\nUsername: ");
        cred.add(sc.nextLine());

        System.out.print("Password: ");
        cred.add(sc.nextLine());

        if (message.equalsIgnoreCase("CREATE_ACCOUNT")) {
            System.out.print("Nome: ");
            cred.add(sc.nextLine());
        }
    }

    public static boolean getNewServer() {
        ObjectInputStream oin;
        DatagramPacket packet;
        ByteArrayInputStream bin;
        Servidor newServer;
        int attempt = 0;

        try {
            socket.close();
            SocketGRDS.setSoTimeout(5000);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (attempt != 3) {
            try {

                packet = new DatagramPacket(ADDR_PORT_REQUEST.getBytes(), ADDR_PORT_REQUEST.length(), AddrGRDS, PortGRDS);
                SocketGRDS.send(packet);

                packet.setData(new byte[MAX_SIZE], 0, MAX_SIZE);

                try {
                    SocketGRDS.receive(packet);
                } catch (SocketTimeoutException e) {
                    attempt++;
                    System.out.println("\nNão foi possível conectar ao GRDS. Tentativas restantes: " + (3 - attempt));
                    continue;
                }

                bin = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                oin = new ObjectInputStream(bin);

                newServer = (Servidor) oin.readObject();

                if (newServer.getListeningPort() != 0){
                    System.out.println("\nNovo endereço IP: " + newServer.getServerAddress().toString());
                    System.out.println("Novo porto de escuta: " + newServer.getListeningPort());
                    socket = new Socket(newServer.getServerAddress(), newServer.getListeningPort());

                    oout = new ObjectOutputStream(socket.getOutputStream());
                    oinS = new ObjectInputStream(socket.getInputStream());

                    request.setMessage(SERVER_REQUEST);
                    oout.writeUnshared(request);
                    oout.flush();

                    request = (Request) oinS.readObject();

                    System.out.println("\n" + request.getMessage() + "\n");
                    return true;
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

//    @Override
//    public void run() {
//        ObjectInputStream oin;
//        String serverWarning;
//        DatagramPacket packet;
//        ByteArrayInputStream bin;
//        Servidor newServer;
//        while (true) {
//            try {
////                oinS = new ObjectInputStream(socket.getInputStream());
////                serverWarning = (String) oinS.readObject();
//
//                if (socket.isClosed()) {
//                    socket.close();
//
//                    packet = new DatagramPacket(ADDR_PORT_REQUEST.getBytes(), ADDR_PORT_REQUEST.length(), AddrGRDS, PortGRDS);
//                    SocketGRDS.send(packet);
//
//                    packet.setData(new byte[MAX_SIZE], 0, MAX_SIZE);
//                    SocketGRDS.receive(packet);
//
//                    bin = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
//                    oin = new ObjectInputStream(bin);
//                    System.out.println("bruh");
//                    newServer = (Servidor) oin.readObject();
//                    if (newServer.getListeningPort() != 0){
//
//
//                    System.out.println("server hostname: " + newServer.getServerAddress().toString());
//                    System.out.println("server port: " + newServer.getListeningPort());
//                    socket = new Socket(newServer.getServerAddress(), newServer.getListeningPort());
//
//                    oinS = new ObjectInputStream(socket.getInputStream());
//                    oout = new ObjectOutputStream(socket.getOutputStream());
//                    }
//                }
//
//            } catch (IOException | ClassNotFoundException e) {
//                e.printStackTrace();
//            }
//        }
//    }
}
