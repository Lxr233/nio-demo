package org.lxr;

/**
 * Hello world!
 *
 */
public class TimeClient
{
    public static void main( String[] args )
    {
        if(args.length !=2){
            System.err.println("Usage: " + TimeClient.class.getSimpleName()+"<host><port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        new Thread(new TimeClentHandler(host,port),"NIO-TimeClient").start();
    }
}
