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
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCounted;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

public class UnsafeAllocationVsDirectMemory {

    public static final int KB = 1024;
    public static final int MB = KB * KB;

    private final MBeanServer platformMBeanServer;
    private final ObjectName directBuffersName;

    public static void main(String[] args) throws Exception {
//        new UnsafeAllocationVsDirectMemory().runTest();
        new UnsafeAllocationVsDirectMemory().fillWithSmallChunksAndFreeBeforeRequestingBigBuffer();
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


    public void fillWithSmallChunksAndFreeBeforeRequestingBigBuffer() throws Exception {
        PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

        List<ByteBuf> buffers = allocateUptoWithBufferOfSize(124 * MB, 8 * KB, allocator);
        System.out.println("Finished allocating 124 Mb memory");
        printDirectMemoryUsed();
        printNettyMemoryUsage(allocator);

        List<ByteBuf> smallBuffers = allocateUptoWithBufferOfSize(8 * KB, 512, allocator);
        System.out.println("Allocate 8KB more");
        printDirectMemoryUsed();
        printNettyMemoryUsage(allocator);

        // free small buffers
        smallBuffers.forEach(ReferenceCounted::release);
        System.out.println("After freed 8KB, before allocating 4MB");
        printDirectMemoryUsed();
        printNettyMemoryUsage(allocator);

        // allocate 1 big 4MB buffer
        ByteBuf bigBuffer = allocator.buffer(4 * MB); // KO
//        allocateUptoWithBufferOfSize(4 * MB, 8 * KB, allocator); // KO
//        allocateUptoWithBufferOfSize(4 * MB, 512, allocator); // OK


//        [2023-09-13T16:03:21,446][INFO ][org.logstash.beats.BeatsHandler][nioEventLoopGroup-3-18][main][beats_in]
//        [local: 127.0.0.1:3333, remote: 127.0.0.1:54754] Handling exception: java.lang.OutOfMemoryError:
//        Cannot reserve 4194304 bytes of direct buffer memory (allocated: 130031617, limit: 134217728)
//        (caused by: java.lang.OutOfMemoryError: Cannot reserve 4194304 bytes of direct buffer memory (allocated: 130031617, limit: 134217728))
    }

    private static List<ByteBuf> allocateUptoWithBufferOfSize(int spaceToFill, int payloadSize, PooledByteBufAllocator allocator) {
        int iterationsToFill = spaceToFill / payloadSize;
        List<ByteBuf> buffers = new ArrayList<>(iterationsToFill);

        // allocates almost all the memory leaving spaces for little less than 4MB
        for (int i = 0; i < iterationsToFill; i++) {
            buffers.add(allocator.buffer(payloadSize));
        }
        return buffers;
    }

    private static void printNettyMemoryUsage(PooledByteBufAllocator allocator) {
        long used = allocator.metric().usedDirectMemory();
        long pinned = allocator.pinnedDirectMemory();
        short ratio = (short) Math.round(((double) pinned / used) * 100);
        System.out.printf("memory status used: %d, pinned: %d, ratio %d%n", used, pinned, ratio);
    }

    private void printDirectMemoryUsed() throws Exception {
        Object directMem = platformMBeanServer.getAttribute(directBuffersName, "MemoryUsed");
        System.out.println("Direct memory allocated: " + directMem);
    }
}