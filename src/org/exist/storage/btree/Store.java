package org.exist.storage.btree;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Path;

/**
 * Interface for basic IO operations used by {@link org.exist.storage.btree.Paged}
 * and its subclasses.
 *
 * @author Wolfgang Meier
 */
public interface Store extends AutoCloseable {

    public final static String CONFIG_ATTRIBUTE = "store";
    public final static String CONFIG_PROPERTY = "db-connection.store";
    public final static String DEFAULT_IMPLEMENTATION = DefaultStore.class.getName();

    enum Mode {
        READ, READ_WRITE
    }

    void open(Path path, Mode mode) throws IOException;

    void seek(long position) throws IOException;

    int read(byte[] data) throws IOException;

    void write(byte[] data) throws IOException;

    FileLock tryLock() throws IOException;

    @Override
    void close() throws IOException;
}
