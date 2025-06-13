# COMPX234-A4
基于UDP协议、实现可靠传输的多线程服务端 + 同步客户端系统

运行调试指南
1.下载UDPserver.java和UDPclient.java以及测试文件
2.在下载目录下打开cmd
3.输入 javac -encoding UTF-8 UDPclient.java UDPserver.java  编译文件
4.开启服务端 java UDPserver 51234
5.开启客户端 java UDPclient localhost 51234 file.txt
6.如果你的file.txt里面有需要下载的文档，即可开始下载
