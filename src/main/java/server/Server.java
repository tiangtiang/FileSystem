package server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * Created by tiang on 2018/5/7.
 */
public class Server {
    private int port;
    public Server(int port){
        this.port = port;
    }

    public void start() throws InterruptedException {
        //负责接收请求的线程
        EventLoopGroup boss = new NioEventLoopGroup();
        //负责处理请求的线程
        EventLoopGroup client = new NioEventLoopGroup();
        try {
            //启动
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(boss, client)
                    //使用NIO Channel
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline =
                                    socketChannel.pipeline();
                            pipeline.addLast("decoder", new HttpRequestDecoder())
                                    .addLast(new HttpObjectAggregator(10*1024*1024))
                                    .addLast(new HttpResponseEncoder())
                                    .addLast("http-chunked", new ChunkedWriteHandler())
                                    .addLast("handle", new FileHandler());
                        }
                    }).option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            System.out.println("server has started at "+port);
            System.out.println("********************");
            ChannelFuture future = boot.bind(port).sync();
            future.channel().closeFuture().sync();
        }finally {
            boss.shutdownGracefully();
            client.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new Server(Config.port).start();
    }
}
