package tv.miqat.companion;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable names for private immutable audio; an unreadable index is never replaced. */
final class PhoneAudioLibrary {
    static final class Entry {
        final String hash, name;
        Entry(String hash, String name) { this.hash = hash; this.name = name; }
    }

    static String validName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid recording name");
        return name;
    }

    static LinkedHashMap<String,String> read(Context c) throws Exception {
        File directory = PhoneAudio.directory(c), index = new File(directory, "library.json");
        LinkedHashMap<String,String> names = new LinkedHashMap<>();
        if (index.exists()) {
            if (!index.isFile() || index.length() > 1048576) throw new IOException("Invalid audio index");
            String encoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(index.toPath()))).toString();
            org.json.JSONTokener input = new org.json.JSONTokener(encoded);
            Object decoded = input.nextValue();
            if (!(decoded instanceof JSONObject) || input.nextClean() != 0)
                throw new IOException("Invalid audio index");
            JSONObject root = (JSONObject) decoded;
            Object version = root.get("version");
            if (!(version instanceof Integer) || ((Integer) version) != 1)
                throw new IOException("Unknown audio index");
            JSONObject entries = root.getJSONObject("entries");
            Iterator<String> ids = entries.keys();
            while (ids.hasNext()) {
                String hash = ids.next();
                if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid audio identity");
                Object name = entries.get(hash);
                if (!(name instanceof String)) throw new IOException("Invalid recording name");
                names.put(hash, validName((String) name));
            }
        }
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot read private audio directory");
        Arrays.sort(files, Comparator.comparing(File::getName));
        boolean adopted = false;
        for (File file : files) {
            if (!file.getName().matches("[a-f0-9]{64}[.]audio")) continue;
            String hash = file.getName().substring(0, 64);
            if (!names.containsKey(hash)) {
                PhoneAudio.file(c, hash);
                names.put(hash, PhoneAlarms.text(c, "phone_audio_import_default") + " " + (names.size() + 1));
                adopted = true;
            }
        }
        if (adopted) write(c, names);
        return names;
    }

    static void write(Context c, Map<String,String> names) throws Exception {
        JSONObject entries = new JSONObject();
        for (Map.Entry<String,String> entry : names.entrySet()) {
            if (!entry.getKey().matches("[a-f0-9]{64}")) throw new IOException("Invalid audio identity");
            entries.put(entry.getKey(), validName(entry.getValue()));
        }
        byte[] bytes = new JSONObject().put("version", 1).put("entries", entries)
                .toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1048576) throw new IOException("Audio index is full");
        File directory = PhoneAudio.directory(c), part = File.createTempFile("library-", ".part", directory);
        try {
            try (FileOutputStream out = new FileOutputStream(part)) {
                out.write(bytes);
                out.getFD().sync();
            }
            Files.move(part.toPath(), new File(directory, "library.json").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(part.toPath()); }
    }
}
