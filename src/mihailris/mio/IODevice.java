package mihailris.mio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IODevice {
    boolean isReadonly();
    InputStream read(String path) throws IOException;
    OutputStream write(String path, boolean append) throws IOException;
    IOPath[] listDir(IOPath path);
    long modificationDate(String path);
    boolean exists(String path);
    boolean isFile(String path);
    boolean isDirectory(String path);
    boolean mkdirs(String path);
    boolean delete(String path);
    File getFile(String path);
}
