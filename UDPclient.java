import java.io.*;
import java.net.*;
import java.util.*;
@SuppressWarnings({"all"})

public class UDPclient {
    private static final int TIMEOUT = 3000; //超时时间：3秒
    private static final int BLOCK_SIZE = 1024; //每次请求的文件块大小

    private DatagramSocket socket; //用于控制通信的socket
    private InetAddress serverAddr;  // 服务器地址
    private int serverPort;// 控制端口

    public UDPclient(String host, int port) throws Exception {
        serverAddr = InetAddress.getByName(host);
        serverPort = port;
        socket = new DatagramSocket(); // 创建客户端控制socket
        socket.setSoTimeout(TIMEOUT); // 设置超时时间
    }

    // 从文件列表中下载多个文件
    public void downloadFilesFromList(String listFilePath) throws IOException {
        //读取请求文件名
        List<String> filenames = readFileList(listFilePath);
        for (String file : filenames) {
            System.out.println("\n开始下载文件: " + file);
            if (!downloadFile(file)) {
                System.out.println("文件下载失败: " + file);
            }
        }
    }

    //读取文件名列表
    private List<String> readFileList(String path) throws IOException {
        List<String> files = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) files.add(line.trim());
            }
        }
        return files;
    }

    //下载文件
    private boolean downloadFile(String filename) throws IOException {
        //发送下载请求
        sendMessage("DOWNLOAD " + filename, serverAddr, serverPort);
        //服务器响应
        String resp = receiveMessage();
        // 判断错误或无响应情况
        if (resp == null || !resp.startsWith("OK")) return false;

        String[] parts = resp.split(" ");
        // 读取文件大小
        long fileSize = Long.parseLong(parts[2]);
        // 获取服务器数据端口
        int dataPort = Integer.parseInt(parts[4]);

        try (
                // 新建数据传输Socket
                DatagramSocket dataSocket = new DatagramSocket();
                // 输出到本地文件
                FileOutputStream fos = new FileOutputStream("downloaded_" + filename)) {

            dataSocket.setSoTimeout(TIMEOUT);  // 设置超时以便重传
            long bytesReceived = 0;
            int lastPercent = -1;

            // 持续请求块直到文件下载完成
            while (bytesReceived < fileSize) {
                int start = (int) bytesReceived;
                int end = (int) Math.min(fileSize - 1, bytesReceived + BLOCK_SIZE - 1);

                String getRequest = "GET " + start + " " + end;
                // 请求特定字节块
                sendMessage(getRequest, serverAddr, dataPort);

                // 预留足够缓冲区
                byte[] buf = new byte[BLOCK_SIZE + 100];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    fos.write(packet.getData(), 0, packet.getLength());// 写入本地
                    bytesReceived += packet.getLength();

                    //优化客户端只输出百分比
                    int percent = (int) (bytesReceived * 100 / fileSize);
                    if (percent != lastPercent && percent % 10 == 0) {
                        System.out.println("进度: " + percent + "%");
                        lastPercent = percent;
                    }
                } catch (SocketTimeoutException e) {
                    // 超时重试当前块
                    System.out.println("超时，重试块: " + start);
                }
            }
            sendMessage("CLOSE", serverAddr, dataPort);
            System.out.println("下载完成: " + filename);
        }

        return true;
    }

    // 使用指定 socket 发送消息（用于数据 socket）
    private void sendMessage(String msg, InetAddress addr, int port) throws IOException {
        byte[] buf = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, addr, port);
        socket.send(packet);
    }

    // 接收控制消息
    private String receiveMessage() {
        try {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            return new String(packet.getData(), 0, packet.getLength()).trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java UDPClient <host> <port> <filelist>");
            return;
        }
        try {
            UDPclient client = new UDPclient(args[0], Integer.parseInt(args[1]));
            client.downloadFilesFromList(args[2]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}