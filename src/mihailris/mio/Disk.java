package mihailris.mio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static mihailris.mio.DiskListener.DiskEvent.*;

public class Disk {
    private static boolean isjar;
    private static String separator;

    public static long totalRead;
    public static long totalWrite;
    private static final Map<String, IODevice> devices = new HashMap<>();

    private static DiskListener listener;

    public static void initialize(){
        String className = Disk.class.getName().replace('.', '/');
        String classJar = Disk.class.getResource("/"+className+".class").toString();
        isjar = classJar.startsWith("jar:");

        separator = System.getProperty("file.separator");
    }

    public static void setListener(DiskListener listener) {
        Disk.listener = listener;
    }

    private static void onEvent(DiskListener.DiskEvent event, IOPath path){
        if (listener != null){
            listener.onEvent(event, path, null);
        }
    }

    private static void onFail(DiskListener.DiskEvent event, IOPath path, String comment, IOException error){
        if (listener != null){
            listener.onFail(event, path, comment, error);
        } else {
            System.err.println("MIO-Disk operation failed ["+event+"]: "+path+" ("+comment+")");
            error.printStackTrace();
        }
    }

    public static void addDevice(String label, IODevice device){
        addDevice(label, device, false);
    }

    public static void addDevice(String label, IODevice device, boolean makedir){
        devices.put(label, device);
        if (makedir) {
            IOPath root = IOPath.get(label+':');
            if (!root.isDirectory())
                root.mkdirs();
        }
    }

    public static void removeDevice(String label){
        devices.remove(label);
    }

    public static void createResDevice(String localDir, String jarDir){
        devices.put("res", new ResDevice(localDir, jarDir));
    }

    public static void createAbsDevice(){
        devices.put("abs", new AbsDevice());
    }

    public static void createDirDevice(String label, File directory){
        if (!directory.isDirectory())
            throw new IllegalArgumentException(directory+" is not a directory");
        devices.put(label, new DirDevice(directory));
    }

    public static IOPath absolute(String path){
        return new IOPath("abs", path);
    }

    public static boolean isJar(){
        return isjar;
    }

    private static OutputStream write(IOPath path, boolean append) throws IOException {
        return getDevice(path.getPrefix(), false).write(path.getPath(), append);
    }

    private static InputStream read(IOPath iopath) throws IOException {
        return getDevice(iopath.getPrefix(), true).read(iopath.getPath());
    }

    public static void writeBytes(IOPath path, byte[] content){
        writeBytes(path, content, false);
    }

    public static void writeBytes(IOPath path, byte[] content, boolean append){
        onEvent(WRITE, path);
        OutputStream output = null;
        try {
            output = write(path, append);
            output.write(content);
            output.close();
            totalWrite += content.length;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            try {
                if (output != null)
                    output.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void writeString(IOPath path, String content){
        writeBytes(path, content.getBytes(), false);
    }

    public static void writeString(IOPath path, String content, boolean append) {
        writeBytes(path, content.getBytes(), append);
    }

    public static long modificationDate(IOPath iopath){
        try {
            return getDevice(iopath.getPrefix(), true).modificationDate(iopath.getPath());
        } catch (IOException e) {
            return -1;
        }
    }

    public static IOPath[] iopathsList(IOPath iopath) {
        onEvent(LIST, iopath);
        try {
            return getDevice(iopath.getPrefix(), true).listDir(iopath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static IODevice getDevice(String label, boolean readonly) throws IOException {
        IODevice device = devices.get(label);
        if (device == null)
            throw new IOException("unknown I/O device '"+label+"'");
        if (!readonly && device.isReadonly())
            throw new IOException("device '"+label+"' is read-only");
        return device;
    }

    public static byte[] readBytes(IOPath iopath) throws IOException {
        onEvent(READ, iopath);
        InputStream input = read(iopath);
        try {
            byte[] bytes = new byte[input.available()];
            int read;
            for (int pos = 0; pos < bytes.length; pos += read) {
                read = input.read(bytes, pos, bytes.length - pos);
                if (read < 0) {
                    throw new EOFException();
                }
            }
            totalRead += bytes.length;
            return bytes;
        } finally {
            input.close();
        }
    }

    public static String readString(IOPath iopath) throws IOException {
        byte[] bytes = readBytes(iopath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String readString(IOPath iopath, String charset) throws IOException {
        byte[] bytes = readBytes(iopath);
        return new String(bytes, charset);
    }

    public static boolean isExist(IOPath iopath) {
        try {
            return getDevice(iopath.getPrefix(), true).exists(iopath.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDirectory(IOPath iopath) {
        try {
            return getDevice(iopath.getPrefix(), true).isDirectory(iopath.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isFile(IOPath iopath) {
        onEvent(MKDIRS, iopath);
        try {
            return getDevice(iopath.getPrefix(), true).isFile(iopath.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean mkdirs(IOPath iopath) {
        try {
            return getDevice(iopath.getPrefix(), false).mkdirs(iopath.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean delete(IOPath iopath) throws IOException {
        onEvent(DELETE, iopath);
        return getDevice(iopath.getPrefix(), false).delete(iopath.getPath());
    }

    public static File getFile(IOPath iopath) {
        try {
            return getDevice(iopath.getPrefix(), true).getFile(iopath.getPath());
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Unzip all content from ZIP file to destination directory.
     * Tries to delete unpacked files on exception
     * @param source target ZIP file
     * @param dest destination directory
     * @throws IOException on I/O errors
     */
    public static void unzip(IOPath source, IOPath dest) throws IOException {
        File file = getFile(source);
        File destDirFile = getFile(dest);
        if (destDirFile == null)
            throw new IOException(dest+" is read-only");

        List<IOPath> created = new ArrayList<>();
        ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
        ZipEntry zipEntry = zis.getNextEntry();
        long available = destDirFile.getUsableSpace();
        while (zipEntry != null){
            IOPath element = dest.child(zipEntry.getName());
            if (zipEntry.isDirectory()){
                if (!element.isDirectory()){
                    element.mkdirs();
                    created.add(element);
                }
            } else {
                try {
                    if (zipEntry.getSize() >= available)
                        throw new IOException("no enough space to unpack "+zipEntry.getName()+
                                " ("+zipEntry.getSize()+" B)");
                    long size = zipEntry.getSize();
                    byte[] bytes = new byte[(int) size];
                    int offset = 0;
                    while (offset < size) {
                        offset += zis.read(bytes, offset, (int) (size-offset));
                    }
                    writeBytes(element, bytes);
                    created.add(element);
                } catch (IOException e){
                    // Revert creations
                    onFail(UNZIP, dest, zipEntry.getName(), e);
                    for (IOPath path : created){
                        try {
                            delete(path);
                        } catch (IOException e1){
                            onFail(DELETE, path, null, e1);
                        }
                    }
                    zis.closeEntry();
                    zis.close();
                    throw e;
                }
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    public static boolean hasDevice(String label) {
        return devices.containsKey(label);
    }
}
