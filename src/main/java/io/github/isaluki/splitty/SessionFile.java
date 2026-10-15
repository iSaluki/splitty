package io.github.isaluki.splitty;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * A tiny file the host rewrites every couple of seconds while its world is open to LAN.
 * The guest instance reads it to find the port to join. Both instances run as the same
 * user on the same machine, so the system temp directory is shared between them.
 */
public final class SessionFile {
	private static final Gson GSON = new Gson();
	private static final long STALE_AFTER_MS = 10_000L;

	public record Session(String id, int port, long updated, String world) {
	}

	private SessionFile() {
	}

	public static void write(Session session) {
		Path path = SplittyConfig.get().sessionPath();
		try {
			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(session));
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Splitty.LOGGER.warn("Could not write session file {}", path, e);
		}
	}

	/** Returns the host's session if the host has refreshed it recently. */
	public static Optional<Session> readFresh() {
		Path path = SplittyConfig.get().sessionPath();
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			Session session = GSON.fromJson(Files.readString(path), Session.class);
			if (session == null || session.id() == null || session.port() <= 0) {
				return Optional.empty();
			}
			if (System.currentTimeMillis() - session.updated() > STALE_AFTER_MS) {
				return Optional.empty();
			}
			return Optional.of(session);
		} catch (IOException | JsonParseException e) {
			return Optional.empty();
		}
	}

	/** Deletes the file, but only if it still belongs to the given session. */
	public static void clear(String id) {
		Path path = SplittyConfig.get().sessionPath();
		try {
			if (Files.isRegularFile(path)) {
				Session session = GSON.fromJson(Files.readString(path), Session.class);
				if (session == null || id.equals(session.id())) {
					Files.deleteIfExists(path);
				}
			}
		} catch (IOException | JsonParseException e) {
			Splitty.LOGGER.debug("Could not clear session file {}", path, e);
		}
	}
}
