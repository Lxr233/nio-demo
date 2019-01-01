package org.lxr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

public class MultiplexerTimeServer implements Runnable{

    private Selector selector;
    private ServerSocketChannel servChannel;
    private volatile boolean stop;

    /**
     * 初始化多路复用器，绑定监听端口
     * @param port
     */
    public MultiplexerTimeServer(int port){
        try{
            //打开ServerSocketChannel，用于监听客户端连接，它是所有客户端连接的父管道
            servChannel = ServerSocketChannel.open();
            //绑定监听端口，设置连接为非阻塞模式
            servChannel.configureBlocking(false);
            servChannel.socket().bind(new InetSocketAddress(port),1024);
            //创建多路复用器Selector
            selector = Selector.open();
            /**
             * 将ServerSocketChannel注册到Selector，监听ACCEPT事件
             * 如果一个Channel要注册到Selector中，这个Channel必须是非阻塞的
             * 因此FileChannel不能使用选择器，因为FileChannel是非阻塞的
             */
            servChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("MultiplexerTimeServer start! port:"+port);
        }
        catch (IOException e){
            //如果资源初始化失败，如端口被占用，则退出
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void stop(){
        this.stop = true;
    }


    @Override
    public void run() {
        /**
         * 多路复用器在线程的run方法的无线循环体内轮训准备就绪的Key
         * 无论是否有读写等事件发生，selector每隔1s都会被唤醒一次
         */
        while(!stop) {
            try {
                /**
                 * 此处设置休眠时间为1s
                 * 可以通过selector.select()方法获取对某事件准备好了的Channel。
                 * 即如果我们在注册时对可写事件感兴趣，那么当select()返回时，我们就可以获取Channel了。
                 * 注意，select()方法返回值表示有多少个Channel可操作。
                 */
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                SelectionKey key = null;
                while (it.hasNext()) {
                    key = it.next();
                    //每次迭代时都要将key从迭代器删除
                    it.remove();
                    try {
                        handleInput(key);
                    } catch (Exception e) {
                        if (key != null) {
                            key.cancel();
                            if (key.channel() != null) {
                                key.channel().close();
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
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
        if(key.isValid()){
            /**
             * 当OP_ACCEPT事件到来时，我们就从ServerSocketChannel中获取一个SocketChannel。
             * SocketChannel代表一个客户端的连接。
             * 注意，在OP_ACCEPT事件中，从key.channel()返回的channel是ServerSocketChannel
             * 而在OP_WRITE和OP_READ中，从key.channel()返回的channel是SocketChannel
             */
            if(key.isAcceptable()){
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                /**
                 * 在OP_ACCEPT到来时，再将这个Channel的OP_READ注册到selector中
                 * 注意，这里我们如果没有设置OP_READ的话，即interest set仍然是OP_CONNECT的话，那么select()方法将会一直返回
                 */
                sc.register(selector,SelectionKey.OP_READ);
            }
            if(key.isReadable()){
                SocketChannel sc = (SocketChannel)key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                if(readBytes>0){
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes,"UTF-8");
                    System.out.println("The time server receive order:"+body);
                    String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body)?
                            new Date(System.currentTimeMillis()).toString():
                            "BAD ORDER";
                    doWrite(sc,currentTime);
                }
                else if (readBytes<0){
                    key.cancel();
                    sc.close();
                }
            }
        }
    }

    private void doWrite(SocketChannel channel, String response) throws IOException{
        if(response!=null && response.trim().length()>0){
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            channel.write(writeBuffer);
        }
    }


}
