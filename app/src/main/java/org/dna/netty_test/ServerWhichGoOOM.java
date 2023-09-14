package org.dna.netty_test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.List;

public class ServerWhichGoOOM {
    private final static Logger LOG = LogManager.getLogger(ServerWhichGoOOM.class);
    public static final int PORT = 1234;
    public static final String HOST = "127.0.0.1";

    public static void main(String[] args) throws Exception {
        runTest();
    }

    private static void runTest() throws Exception {
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_LINGER, 0) // Since the protocol doesn't support yet a remote close from the server and we don't want to have 'unclosed' socket lying around we have to use `SO_LINGER` to force the close of the socket.
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

//                            pipeline.addLast("idle-state-handler",
//                                    new IdleStateHandler(10, 5, 15));
                            pipeline.addLast("splitter", new Splitter());
                            pipeline.addLast("memory-leaker", new MemoryLeaker());

                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception {
                                    LOG.warn("Exception received {}", cause.getMessage());
                                    super.exceptionCaught(ctx, cause);
                                }
                            });
                        }
                    });

            Channel channel = server.bind(HOST, PORT).sync().channel();
            channel.closeFuture().sync();
        } finally {
            workGroup.shutdownGracefully().sync();
        }
    }


    private static class Splitter extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < 4 * 1024) {
                return;
            }
            while (in.readableBytes() >= 4 * 1024) {
                ByteBuf segment = ctx.alloc().buffer(4 * 1024);
                in.readBytes(segment);
                out.add(segment);
            }
        }
    }

    private static class MemoryLeaker extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf payload = (ByteBuf) msg;
            String command = payload.toString(CharsetUtil.UTF_8).strip();
//            System.out.println("Command received: " +  command.substring(0, 10));
            // do not release the buffer in ChannelInboundHandlerAdapter generate a memory leak.

//            throw new IllegalArgumentException("Unrecognized payload");

//            System.out.println("Command received: " + command);
//            if ("name".equalsIgnoreCase(command)) {
//                System.out.println(InetAddress.getLocalHost().getHostName());
//            } else {
//                throw new IllegalArgumentException("Unrecognized command: " + command);
//            }
        }
    }
}
