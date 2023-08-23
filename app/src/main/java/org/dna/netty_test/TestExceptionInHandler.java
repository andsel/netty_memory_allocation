///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.netty:netty-buffer:4.1.87.Final
//DEPS org.apache.logging.log4j:log4j-api:2.17.1
//DEPS org.apache.logging.log4j:log4j-core:2.17.1


// Run limiting the direct memory size with:
// jbang -Dlog4j.configurationFile=log4j2.properties -Dio.netty.maxDirectMemory=-1 -Dio.netty.allocator.numDirectArenas=1 -Dio.netty.allocator.numHeapArenas=0 -R-XX:MaxDirectMemorySize=8388608 UnsafeAllocationVsDirectMemory.java

package org.dna.netty_test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class TestExceptionInHandler {

    private final static Logger LOG = LogManager.getLogger(TestExceptionInHandler.class);

    public static void main(String[] args) throws Exception {
        new TestExceptionInHandler().runTest();
    }

    private TestExceptionInHandler() throws MalformedObjectNameException {
    }

    public void runTest() throws Exception {

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

                            pipeline.addLast("idle-state-handler",
                                    new IdleStateHandler(10, 5, 15));
                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ByteBuf payload = (ByteBuf) msg;
                                    String command = payload.toString(CharsetUtil.UTF_8).strip();
                                    System.out.println("Command received: " + command);
                                    if ("name".equalsIgnoreCase(command)) {
                                        System.out.println(InetAddress.getLocalHost().getHostName());
                                    } else {
                                        throw new IllegalArgumentException("Unrecognized command: " + command);
                                    }
                                }
                            });

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

            Channel channel = server.bind("127.0.0.1", 1234).sync().channel();
            channel.closeFuture().sync();
        } finally {
            workGroup.shutdownGracefully().sync();
        }
    }
}