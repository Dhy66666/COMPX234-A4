import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class UDPclient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000;
    private static final int BLOCK_SIZE = 1000;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java UDPclient <hostname> <port> <filelist>");
            return;
        }

        InetAddress serverAddress = InetAddress.getByName(args[0]);
        int serverPort = Integer.parseInt(args[1]);
        String fileListPath = args[2];

        DatagramSocket socket = new DatagramSocket();
        List<String> fileList = readFileList(fileListPath);

        for (String filename : fileList) {
            System.out.println("Requesting: " + filename);
            String response = sendAndReceive(socket, serverAddress, serverPort, "DOWNLOAD " + filename);

            if (response.startsWith("ERR")) {
                System.out.println("File not found: " + filename);
                continue;
            }

            String[] parts = response.split(" ");
            long fileSize = Long.parseLong(parts[3]);
            int dataPort = Integer.parseInt(parts[5]);

            FileOutputStream fos = new FileOutputStream(filename);
            int downloaded = 0;

            while (downloaded < fileSize) {
                int end = Math.min(downloaded + BLOCK_SIZE - 1, (int) fileSize - 1);
                String fileRequest = "FILE " + filename + " GET START " + downloaded + " END " + end;
                String dataResp = sendAndReceive(socket, serverAddress, dataPort, fileRequest);

                // 解析返回数据并写入文件
                int index = dataResp.indexOf("DATA");
                String base64 = dataResp.substring(index + 5).trim();
                byte[] data = Base64.getDecoder().decode(base64);
                fos.write(data);
                downloaded += data.length;
                System.out.print("*");
            }

            // 通知服务器关闭文件
            String closeMsg = "FILE " + filename + " CLOSE";
            String closeResp = sendAndReceive(socket, serverAddress, dataPort, closeMsg);
            if (closeResp.startsWith("FILE") && closeResp.contains("CLOSE_OK")) {
                System.out.println("\nFinished downloading: " + filename);
            }

            fos.close();
        }

        socket.close();
    }

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
