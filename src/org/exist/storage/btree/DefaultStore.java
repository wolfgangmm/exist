package org.exist.storage.btree;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

/**
 * Default Store implementation based on {@link java.io.RandomAccessFile}.
 */
public class DefaultStore implements Store {


    private RandomAccessFile raf;

    @Override
    public void open(Path path, Mode mode) throws IOException {
        raf = new RandomAccessFile(path.toFile(), mode == Mode.READ_WRITE ? "rw" : "r");
    }

    @Override
    public FileLock tryLock() throws IOException {
        final FileChannel channel = raf.getChannel();
        return channel.tryLock();
    }

    @Override
    public void seek(long position) throws IOException {
        if (raf.getFilePointer() != position) {
            raf.seek(position);
        }
    }

    @Override
    public int read(byte[] data) throws IOException {
        return raf.read(data);
    }

    @Override
    public void write(byte[] data) throws IOException {
        raf.write(data);
    }

    @Override
    public void close() throws IOException {
        if (raf != null) {
            raf.close();
        }
        raf = null;
    }
}
