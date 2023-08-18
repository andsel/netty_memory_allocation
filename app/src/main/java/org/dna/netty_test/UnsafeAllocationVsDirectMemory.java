///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.netty:netty-buffer:4.1.87.Final
//DEPS org.apache.logging.log4j:log4j-api:2.17.1
//DEPS org.apache.logging.log4j:log4j-core:2.17.1


// Run limiting the direct memory size with:
// jbang -Dlog4j.configurationFile=log4j2.properties -Dio.netty.maxDirectMemory=-1 -Dio.netty.allocator.numDirectArenas=1 -Dio.netty.allocator.numHeapArenas=0 -R-XX:MaxDirectMemorySize=8388608 UnsafeAllocationVsDirectMemory.java

package org.dna.netty_test;

import java.util.ArrayList;
import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import javax.management.*;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

public class UnsafeAllocationVsDirectMemory {

    private final MBeanServer platformMBeanServer;
    private final ObjectName directBuffersName;

    public static void main(String[] args) throws Exception {
        new UnsafeAllocationVsDirectMemory().runTest();
    }

    private UnsafeAllocationVsDirectMemory() throws MalformedObjectNameException {
        this.platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        this.directBuffersName = new ObjectName("java.nio:name=direct,type=BufferPool");
    }

    public void runTest() throws Exception {

        // final ByteBuffer buffer = ByteBuffer.allocateDirect(2<<20);
        // allocate 4MB using Netty's direct, so it should use Unsafe and shouldn't be accounted into the MaxDirectMemorySize
        final ByteBuf nettyDirectBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(4<<20, 4<<20);
        System.out.println("After Netty direct (Unsafe) allocation of 4MB");
        printDirectMemoryUsed();

        // allocate a small 8K buffer
        final ByteBuffer smallNioBuffer = ByteBuffer.allocateDirect(8 * 1024);

        // Allocate 2MB with NIO direct buffer, so this should be counted
        final ByteBuffer nioBuffer = ByteBuffer.allocateDirect(2<<20);
        System.out.println("After allocation of NIO direct space of 2MB");
        printDirectMemoryUsed();

        // MaxDirectMemorySize is 8MB and io.netty.maxDirectMemory is -1 so we should have
        // 4 MB (on a total of 8MB availables) for Netty's direct buffers, ouside of JVM direct memory, just native memory
        // 2 MB + 8KB (on a total of 8MB availables) JVM IO direct memory
        // allocating other 2MB in NIO direct space, should be permitted...

        // Allocate 2MB with NIO direct buffer, so this should be counted
        final ByteBuffer anotherNioBuffer = ByteBuffer.allocateDirect(2<<20);
        System.out.println("After allocation of another 2MB in NIO direct space");
        printDirectMemoryUsed();
    }

    private void printDirectMemoryUsed() throws Exception {
        Object directMem = platformMBeanServer.getAttribute(directBuffersName, "MemoryUsed");
        System.out.println("Direct memory allocated: " + directMem);
    }
}