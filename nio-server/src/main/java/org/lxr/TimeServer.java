package org.lxr;

/**
 * Hello world!
 *
 */
public class TimeServer
{
    public static void main( String[] args )
    {
        if(args.length!=1){
            System.err.println("Usage: "+ TimeServer.class.getSimpleName()+"<port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        MultiplexerTimeServer multiplexerTimeServer = new MultiplexerTimeServer(port);
        new Thread(multiplexerTimeServer,"NIO-TimeServer").start();
    }
}
