import java.io.*;
import java.net.*;
@SuppressWarnings({"all"})

public class UDPserver {
    // 单次数据块大小
    private static final int BUFFER_SIZE = 1024;
    // 控制Socket，监听控制命令
    private DatagramSocket controlSocket;

    public UDPserver(int port) throws SocketException {
        // 初始化控制端口
        controlSocket = new DatagramSocket(port);
        System.out.println("Server started on port " + port);
    }

    // 启动服务器，持续监听客户端请求
    public void start() {
        // 控制消息缓冲区
        byte[] buf = new byte[2048];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                // 接收控制请求
                controlSocket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                System.out.println("收到消息: [" + msg + "] 来自 " + clientAddr + ":" + clientPort);
                
                if (msg.startsWith("DOWNLOAD ")) {
                    // 提取文件名
                    String filename = msg.substring(9).trim();
                    File file = new File(filename);

                    // 文件不存在
                    if (!file.exists()) {
                        sendMessage("ERR NOT_FOUND", clientAddr, clientPort);
                        continue;
                    }

                    long fileSize = file.length();
                    // 新建数据传输Socket
                    DatagramSocket dataSocket = new DatagramSocket();
                    int dataPort = dataSocket.getLocalPort();

                    sendMessage("OK SIZE " + fileSize + " PORT " + dataPort, clientAddr, clientPort);

                    // 启动新线程处理该客户端的下载请求
                    new Thread(() -> {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            while (true) {
                                DatagramPacket reqPacket = new DatagramPacket(new byte[2048], 2048);
                                // 接收数据请求
                                dataSocket.receive(reqPacket);

                                String request = new String(reqPacket.getData(), 0, reqPacket.getLength()).trim();
                                // 客户端结束下载
                                if (request.equals("CLOSE")) break;

                                if (request.startsWith("GET ")) {
                                    String[] parts = request.split(" ");
                                    int start = Integer.parseInt(parts[1]);
                                    int end = Integer.parseInt(parts[2]);
                                    int len = end - start + 1;

                                    // 定位文件指针并读取指定字节块
                                    fis.getChannel().position(start);
                                    int readLen = fis.read(buffer, 0, len);
                                    if (readLen > 0) {
                                        DatagramPacket sendPacket = new DatagramPacket(
                                                buffer, readLen, reqPacket.getAddress(), reqPacket.getPort());
                                        dataSocket.send(sendPacket);// 发送数据块
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            dataSocket.close();// 关闭数据Socket
                        }
                    }).start();
                } else {
                    // 非法请求
                    sendMessage("INVALID", clientAddr, clientPort);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 发送响应消息
    private void sendMessage(String msg, InetAddress addr, int port) throws IOException {
        byte[] data = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        controlSocket.send(packet);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java UDPServer <port>");
            return;
        }
        new UDPserver(Integer.parseInt(args[0])).start();
    }
}


