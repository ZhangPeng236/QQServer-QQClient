package com.hspedu.qqserver.service;

import com.hspedu.qqcommon.Message;
import com.hspedu.qqcommon.MessageType;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author 韩顺平
 * @version 1.0
 * 该类的一个对象和某个客户端保持通信
 */
public class ServerConnectClientThread extends Thread {
    private Socket socket;
    private String userId;//连接到服务端的用户id
    private ObjectInputStream ois;
    private ObjectOutputStream oos;

    public ServerConnectClientThread(Socket socket, String userId, ObjectOutputStream oos, ObjectInputStream ois) {
        this.socket = socket;
        this.userId = userId;
        this.oos = oos;
        this.ois = ois;
    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectOutputStream getOos() {
        return oos;
    }

    public ObjectInputStream getOis() {
        return ois;
    }

    @Override
    public void run() { //这里线程处于run的状态，可以发送/接收消息
        while (true) {
            try {
                System.out.println(Thread.currentThread().getName() + "服务端和客户端" + userId + "保持通信，读取数据...");
                Message message = (Message) ois.readObject();
                //后面会使用message, 根据message的类型，做相应的业务处理
                if (message.getMesType().equals(MessageType.MESSAGE_GET_ONLINE_FRIEND)) {
                    //客户端要在线用户列表
                    /*
                    在线用户列表形式 100  200  紫霞仙子
                     */
                    System.out.println(message.getSender() + " 要在线用户列表");
                    String onlineUser = ManageClientThreads.getOnlineUser();
                    //返回message
                    //构建一个Message 对象，返回给客户端
                    Message message2 = new Message();
                    message2.setMesType(MessageType.MESSAGE_RET_ONLINE_FRIEND);
                    message2.setContent(onlineUser);
                    message2.setGetter(message.getSender());
                    //返回给客户端
                    oos.writeObject(message2);
                } else if (message.getMesType().equals(MessageType.MESSAGE_COMM_MES)) {
                    ServerConnectClientThread serverConnectClientThread = ManageClientThreads.getServerConnectClientThread(message.getGetter());
                    if (serverConnectClientThread != null) {
                        //得到对应socket的对象输出流，将message对象转发给指定的客户端
                        serverConnectClientThread.oos.writeObject(message);
                        serverConnectClientThread.oos.flush();
                    } else {
                        ArrayList<Message> offlineMessage = QQServer.offlineDb.get(message.getGetter());
                        if (offlineMessage == null) {
                            offlineMessage = new ArrayList<>();
                            offlineMessage.add(message);
                            QQServer.offlineDb.put(message.getGetter(), offlineMessage);
                        } else {
                            offlineMessage.add(message);
                            QQServer.offlineDb.put(message.getGetter(), offlineMessage);
                        }
                    }
                } else if (message.getMesType().equals(MessageType.MESSAGE_TO_ALL_MES)) {
                    //需要遍历 管理线程的集合，把所有的线程的socket得到，然后把message进行转发即可
                    HashMap<String, ServerConnectClientThread> hm = ManageClientThreads.getHm();
                    for (String onLineUserId : hm.keySet()) {
                        if (!onLineUserId.equals(message.getSender())) {
                            //进行转发message
                            hm.get(onLineUserId).oos.writeObject(message);
                            hm.get(onLineUserId).oos.flush();
                        }
                    }
                } else if (message.getMesType().equals(MessageType.MESSAGE_FILE_MES)) {
                    //根据getter id 获取到对应的线程，将message对象转发
                    ManageClientThreads.getServerConnectClientThread(message.getGetter()).oos.writeObject(message);
                    ManageClientThreads.getServerConnectClientThread(message.getGetter()).oos.flush();
                } else if (message.getMesType().equals(MessageType.MESSAGE_CLIENT_EXIT)) {//客户端退出
                    System.out.println("客户端" + message.getSender() + "正在退出");
                    // 先发确认消息
                    Message exitAck = new Message();
                    exitAck.setMesType(MessageType.MESSAGE_CLIENT_EXIT);
                    oos.writeObject(exitAck);
                    oos.flush();
                    // 再清理
                    ManageClientThreads.removeServerConnectClientThread(message.getSender());
                    socket.close();
                    break;
                } else {
                    System.out.println("其他类型的message , 暂时不处理");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}