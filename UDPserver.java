import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Base64;

public class UDPserver {
    private static final int DATA_PORT_MIN = 50000;
    private static final int DATA_PORT_MAX = 51000;
    private static final int BUFFER_SIZE = 2048;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java UDPserver <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        DatagramSocket serverSocket = new DatagramSocket(port);
        byte[] receiveBuffer = new byte[BUFFER_SIZE];
        System.out.println("Server started on port " + port);

        while (true) {
            DatagramPacket requestPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            serverSocket.receive(requestPacket);
            String request = new String(requestPacket.getData(), 0, requestPacket.getLength()).trim();
            System.out.println("Received: " + request);

            String[] parts = request.split(" ");
            if (parts.length == 2 && parts[0].equals("DOWNLOAD")) {
                String filename = parts[1];
                File file = new File(filename);
                InetAddress clientAddress = requestPacket.getAddress();
                int clientPort = requestPacket.getPort();

                if (!file.exists()) {
                    String errMsg = "ERR " + filename + " NOT_FOUND";
                    byte[] errData = errMsg.getBytes();
                    serverSocket.send(new DatagramPacket(errData, errData.length, clientAddress, clientPort));
                } else {
                    long fileSize = file.length();
                    int dataPort = getRandomPort();
                    String okMsg = "OK " + filename + " SIZE " + fileSize + " PORT " + dataPort;
                    byte[] okData = okMsg.getBytes();
                    serverSocket.send(new DatagramPacket(okData, okData.length, clientAddress, clientPort));

                    // 启动线程处理文件数据传输
                    new Thread(() -> handleFileTransmission(file, filename, clientAddress, dataPort)).start();
                }
            }
        }
    }

    private static void handleFileTransmission(File file, String filename, InetAddress clientAddress, int port) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            DatagramSocket socket = new DatagramSocket(port);
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("Thread started on port " + port);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                String[] parts = msg.split(" ");

                if (parts[0].equals("FILE") && parts[1].equals(filename)) {
                    if (parts[2].equals("CLOSE")) {
                        String closeResp = "FILE " + filename + " CLOSE_OK";
                        socket.send(new DatagramPacket(closeResp.getBytes(), closeResp.length(), clientAddress, packet.getPort()));
                        break;
                    } else if (parts.length >= 8 && parts[2].equals("GET")) {
                        int start = Integer.parseInt(parts[4]);
                        int end = Integer.parseInt(parts[6]);
                        int length = end - start + 1;

                        byte[] fileData = new byte[length];
                        raf.seek(start);
                        raf.read(fileData);

                        String base64 = Base64.getEncoder().encodeToString(fileData);
                        String resp = "FILE " + filename + " OK START " + start + " END " + end + " DATA " + base64;
                        socket.send(new DatagramPacket(resp.getBytes(), resp.length(), clientAddress, packet.getPort()));
                    }
                }
            }

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getRandomPort() {
        return DATA_PORT_MIN + new Random().nextInt(DATA_PORT_MAX - DATA_PORT_MIN + 1);
    }
}

