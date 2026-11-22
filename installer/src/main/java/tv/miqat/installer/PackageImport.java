package tv.miqat.installer;

import java.io.*;
import java.nio.file.*;

/** A failed local package selection must leave the previous valid package intact. */
public final class PackageImport {
    private PackageImport() {}

    public interface Validation {
        void check(File file) throws IOException;
    }

    public static File copy(InputStream input, File target, long maximum, Validation validation)
            throws IOException {
        if (input == null) throw new IOException("Cannot open package");
        try (InputStream in = input) {
            File part = File.createTempFile("miqat-package-", ".part", target.getParentFile());
            try {
                try (FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[65536];
                    long count = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted())
                            throw new InterruptedIOException("Package selection cancelled");
                        count += n;
                        if (count > maximum) throw new IOException("Package is too large");
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                validation.check(part);
                Files.move(
                        part.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                return target;
            } finally {
                Files.deleteIfExists(part.toPath());
            }
        }
    }
}
