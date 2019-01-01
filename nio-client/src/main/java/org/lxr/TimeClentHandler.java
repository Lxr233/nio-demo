package org.lxr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class TimeClentHandler implements Runnable {

    private String host;
    private int port;
    private Selector selector;
    private SocketChannel socketChannel;
    private volatile boolean stop;

    public TimeClentHandler(String host, int port) {
        this.host = host==null?"127.0.0.1":host;
        this.port = port;
        try{
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
        }catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void run() {
        try{
            doConnct();
        }
        catch (IOException e){
            e.printStackTrace();
            System.exit(1);
        }
        while(!stop){
            try{
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                SelectionKey key = null;
                while(it.hasNext()){
                    key = it.next();
                    it.remove();
                    try{
                        handleInput(key);
                    }catch (Exception e){
                        if(key!=null){
                            key.cancel();
                            if(key.channel()!=null){
                                key.channel().close();
                            }
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                System.exit(1);
            }
        }
        /**
         * 多路复用器关闭后，所有注册在上面的Channel和Pipe等资源都会被自动去注册并关闭
         * 所以不需要重复释放资源
         */
        if(selector!=null){
            try {
                selector.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws IOException{
        /**
         * 先对key进行判断，看处于什么状态。
         * 如果是处于连接状态，说明服务端已经返回ACK应答消息。
         */
        if(key.isValid()){
            SocketChannel sc = (SocketChannel) key.channel();
            if(key.isConnectable()){
                /**
                 * 这时需要对连接结果进行判断，调用SocketChannel的finishConnect()
                 * 如果返回值为true，则说明客户端连接成功
                 * 如果返回false则直接抛异常，说明连接失败。
                 */
                if(sc.finishConnect()){
                    /**
                     * 连接成功，将SocketChannel注册到多路复用器上，注册SelectionKey.OP_READ，监听网络读操作
                     * 然后发送请求消息给服务端
                     */
                    sc.register(selector,SelectionKey.OP_READ);
                    doWrite(sc);
                }
                else {
                    //连接失败进程退出
                    System.exit(1);
                }
            }
            //如果客户端接收到了服务端的应答消息，则SocketChannel是可读的
            if(key.isReadable()){
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                /**
                 * sc.read(readBuffer)是异步操作，所以必须对读取结果进行判断
                 */
                if(readBytes>0){
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes,"UTF-8");
                    System.out.println("Now is :"+body);
                    this.stop = true;
                }else if(readBytes<0){
                    key.cancel();
                    sc.close();
                }
            }
        }

    }

    private void doConnct() throws IOException{
        /**
         * 判断是否连接成功
         * 如果连接成功，则将SocketChannel注册到Selector上，注册SelectionKey.OP_READ。
         * 如果没有直连成功，说明服务端没有返回TCP握手应答消息，但这并不代表连接失败。
         * 需要将SocketChannel注册到Selector上，注册SelectionKey.OP_CONNECT，
         * 当服务端返回TCP syn-ack消息后，Selector就能够轮询到这个SocketChannel处于连接就绪状态。
         *
         */
        if(socketChannel.connect(new InetSocketAddress(host,port))){
            socketChannel.register(selector, SelectionKey.OP_READ);
            doWrite(socketChannel);
        }
        else {
            socketChannel.register(selector,SelectionKey.OP_CONNECT);
        }
    }

    private void doWrite(SocketChannel socketChannel) throws IOException{
        byte[] req = "QUERY TIME ORDER".getBytes();
        ByteBuffer writeBuffer = ByteBuffer.allocate(req.length);
        writeBuffer.put(req);
        writeBuffer.flip();
        socketChannel.write(writeBuffer);
        if(!writeBuffer.hasRemaining()){
            System.out.println("Send order to server succeed");
        }
    }
}
