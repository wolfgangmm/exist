package org.exist.storage.btree;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Implements IO operations based on memory mapped files.
 * The class maintains a list of memory mapped regions
 * of fixed size.
 */
public class NIOStore implements Store {

    public final static int CHUNK_SIZE = 64 * 1024 * 1024;

    private FileChannel channel = null;
    private FileChannel.MapMode mapMode = FileChannel.MapMode.READ_WRITE;
    private List<MappedByteBuffer> buffers = new ArrayList<MappedByteBuffer>();
    private int currentBuffer = 0;

    public NIOStore() throws IOException {
    }

    @Override
    public void open(Path path, Mode mode) throws IOException {
        if (mode == Mode.READ_WRITE) {
            channel = (FileChannel) Files.newByteChannel(path, READ, WRITE, CREATE);
            mapMode = FileChannel.MapMode.READ_WRITE;
        } else {
            channel = (FileChannel) Files.newByteChannel(path, READ);
            mapMode = FileChannel.MapMode.READ_ONLY;
        }
        final long size = channel.size();
        if (size > 0) {
            final long chunks;
            if (size % CHUNK_SIZE == 0) {
                chunks = size / CHUNK_SIZE;
            } else {
                chunks = size / CHUNK_SIZE + 1;
            }
            //System.console().printf("File %s. Size: %d. Opening chunks: %d\n", path, size, chunks);
            for (long i = 0; i < chunks; i++) {
                final MappedByteBuffer mapped = channel.map(mapMode, i * CHUNK_SIZE, CHUNK_SIZE);
                buffers.add(mapped);
            }
        } else {
            buffers.add(channel.map(mapMode, 0, CHUNK_SIZE));
        }
    }

    @Override
    public FileLock tryLock() throws IOException {
        return channel.tryLock();
    }

    @Override
    public void seek(long position) throws IOException {
        currentBuffer = (int)(position / CHUNK_SIZE);
        growIfNeeded(currentBuffer);
        int offset = (int)(position % CHUNK_SIZE);
        MappedByteBuffer chunk = buffers.get(currentBuffer);
        if (chunk.position() != offset) {
            chunk.position(offset);
        }
    }

    @Override
    public int read(byte[] data) {
        int remaining = data.length;
        int offset = 0;
        while (remaining > 0) {
            MappedByteBuffer chunk = buffers.get(currentBuffer);
            if (offset > 0) {
                chunk.position(0);
            }
            int bytesToRead = remaining;
            final int position = chunk.position();
            if (position + remaining >= CHUNK_SIZE) {
                bytesToRead = CHUNK_SIZE - position;
                ++currentBuffer;
            }
            chunk.get(data, offset, bytesToRead);
            offset += bytesToRead;
            remaining -= bytesToRead;
        }
        return data.length;
    }

    @Override
    public void write(byte[] data) throws IOException {
        int remaining = data.length;
        int offset = 0;
        while(remaining > 0) {
            growIfNeeded(currentBuffer);
            MappedByteBuffer chunk = buffers.get(currentBuffer);
            if (offset > 0) {
                chunk.position(0);
            }
            int bytesToWrite = remaining;
            final int position = chunk.position();
            if (position + remaining >= CHUNK_SIZE) {
                bytesToWrite = CHUNK_SIZE - position;
                ++currentBuffer;
            }
            chunk.put(data, offset, bytesToWrite);
            remaining -= bytesToWrite;
            offset += bytesToWrite;
        }
    }

    private void growIfNeeded(long chunkIdx) throws IOException {
        if (chunkIdx == buffers.size()) {
            final MappedByteBuffer mapped = channel.map(mapMode, chunkIdx * CHUNK_SIZE, CHUNK_SIZE);
            buffers.add(mapped);
        }
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
        buffers = null;
        channel = null;
    }
}
