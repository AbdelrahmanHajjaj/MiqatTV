package tv.miqat.companion;

import android.content.Context;
import android.media.*;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Imports only bounded local audio; settings reference an immutable private content identity. */
final class PhoneAudio {
    static final long MAX_BYTES = 20L * 1024 * 1024;

    static File directory(Context context) throws IOException {
        File directory =
                new File(
                        context.createDeviceProtectedStorageContext().getFilesDir(),
                        "custom-audio");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create private audio directory");
        return directory;
    }

    static String slot(String id) {
        if (id.equals("reminders.voice")) return "default";
        for (String prayer : PhoneStore.PRAYERS)
            if (!prayer.equals("sunrise") && id.equals("call." + prayer + ".voice")) return prayer;
        throw new IllegalArgumentException("Invalid audio slot");
    }

    static void importFile(Context context, InputStream input, String id) throws Exception {
        importFile(context, input, id, PhoneAlarms.text(context, "phone_audio_import_default"));
    }

    static void importFile(Context context, InputStream input, String id, String name) throws Exception {
        if (input == null) throw new IOException("No audio file");
        try (InputStream in = input) {
            String slot = slot(id);
            String label = PhoneAudioLibrary.validName(name);
            File directory = directory(context),
                    part = File.createTempFile("import-", ".part", directory);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long count = 0;
                try (FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted())
                            throw new InterruptedIOException("Import cancelled");
                        count += n;
                        if (count > MAX_BYTES) throw new IOException("Audio exceeds 20 MB");
                        digest.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                validateMedia(part);
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest())
                    hash.append(String.format(Locale.US, "%02x", b & 255));
                synchronized (PhoneStore.LOCK) {
                    Map<String,String> names = PhoneAudioLibrary.read(context);
                    File file = new File(directory, hash + ".audio");
                    Files.move(
                            part.toPath(),
                            file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    names.put(hash.toString(), label);
                    PhoneAudioLibrary.write(context, names);
                    PhoneStore current = PhoneStore.read(context);
                    Map<String, String> edits = new LinkedHashMap<>();
                    edits.put(id, "custom");
                    edits.put("audio.custom." + slot, hash.toString());
                    current.changed(edits).save(context);
                    try {
                        cleanup(context);
                    } catch (Exception ignored) {
                        /* Committed audio remains selected; reclaim unused files on the next import. */
                    }
                }
            } finally {
                Files.deleteIfExists(part.toPath());
            }
        }
    }

    private static void validateMedia(File file) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(file.getPath());
            long duration =
                    Long.parseLong(
                            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration < 1
                    || duration > 600000
                    || metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                            == null
                    || metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                            != null) throw new IOException("Not bounded audio-only media");
        } finally {
            metadata.release();
        }
        MediaPlayer decoder = new MediaPlayer();
        try {
            decoder.setDataSource(file.getPath());
            decoder.prepare();
        } finally {
            decoder.release();
        }
    }

    static File file(Context c, PhoneStore settings, int prayer, boolean defaults)
            throws IOException {
        String slot = defaults ? "default" : PhoneStore.PRAYERS[prayer];
        String hash = settings.value("audio.custom." + slot);
        return file(c, hash);
    }

    static File file(Context c, String hash) throws IOException {
        if (!hash.matches("[a-f0-9]{64}")) throw new IOException("No imported audio");
        File file = new File(directory(c), hash + ".audio");
        if (!file.isFile() || file.length() < 1 || file.length() > MAX_BYTES)
            throw new IOException("Private audio unavailable");
        return file;
    }

    static List<PhoneAudioLibrary.Entry> library(Context c) throws Exception {
        synchronized (PhoneStore.LOCK) {
            List<PhoneAudioLibrary.Entry> entries = new ArrayList<>();
            for (Map.Entry<String,String> item : PhoneAudioLibrary.read(c).entrySet())
                entries.add(new PhoneAudioLibrary.Entry(item.getKey(), item.getValue()));
            entries.sort(Comparator.comparing(item -> item.name.toLowerCase(Locale.ROOT)));
            return entries;
        }
    }

    static String name(Context c, PhoneStore settings, String id) throws Exception {
        String hash = settings.value("audio.custom." + slot(id));
        synchronized (PhoneStore.LOCK) {
            String label = PhoneAudioLibrary.read(c).get(hash);
            return label == null ? PhoneAlarms.text(c, "phone_custom") : label;
        }
    }

    static void select(Context c, String id, String hash) throws Exception {
        synchronized (PhoneStore.LOCK) {
            if (!PhoneAudioLibrary.read(c).containsKey(hash)) throw new IOException("Unknown recording");
            file(c, hash);
            Map<String,String> changes = new LinkedHashMap<>();
            changes.put(id, "custom");
            changes.put("audio.custom." + slot(id), hash);
            PhoneStore.read(c).changed(changes).save(c);
        }
    }

    static void cleanup(Context c) throws Exception {
        synchronized (PhoneStore.LOCK) {
            PhoneStore settings = PhoneStore.read(c);
            Set<String> keep = new HashSet<>();
            for (String hash : PhoneAudioLibrary.read(c).keySet()) keep.add(hash + ".audio");
            for (Map.Entry<String, String> value : settings.values.entrySet())
                if (value.getKey().startsWith("audio.custom.") && !value.getValue().isEmpty())
                    keep.add(value.getValue() + ".audio");
            File[] files = directory(c).listFiles();
            if (files == null) throw new IOException("Cannot read private audio directory");
            for (File file : files)
                if (file.getName().matches("[a-f0-9]{64}[.]audio")
                        && !keep.contains(file.getName())) Files.delete(file.toPath());
        }
    }
}
