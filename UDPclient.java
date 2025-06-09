import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

class UDPclient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000;
    private static final int BLOCK_SIZE = 1000;

    private static List<String> readFileList(String filePath) throws IOException {
        List<String> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                list.add(line);
            }
        }
        br.close();
        return list;
    }

    private static String sendAndReceive(DatagramSocket socket, InetAddress address, int port, String msg) throws IOException {
        byte[] sendData = msg.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        byte[] receiveBuffer = new byte[2048];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        int timeout = INITIAL_TIMEOUT;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                socket.send(sendPacket);
                socket.setSoTimeout(timeout);
                socket.receive(receivePacket);
                return new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout (" + attempt + "), retrying...");
                timeout *= 2;
            }
        }
        throw new IOException("No response after retries");
    }
}
