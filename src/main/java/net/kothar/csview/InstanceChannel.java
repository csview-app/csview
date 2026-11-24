package net.kothar.csview;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.freedesktop.BaseDirectory;

/**
 * Lets a second launch of CSView hand its files to the instance that is already running, so that
 * they open as new windows of that instance.
 *
 * <p>The running instance listens on a Unix domain socket inside a directory that only the current
 * user can enter, so the operating system, rather than anything in this protocol, decides who may
 * connect. This replaced a TCP port on 127.0.0.1, which any local user could reach, and which
 * accepted a Java-serialized object from whoever connected.
 *
 * <p>A request is a magic number, a count, and that many paths written with
 * {@link DataOutput#writeUTF}. The running instance answers with a single {@code 1} once it has
 * taken the paths. Anything else, including silence, tells the new launch to open its own window.
 */
public class InstanceChannel {

    /** "CSV1": identifies the protocol, and its version, should it ever need to change. */
    static final int MAGIC = 0x43535631;

    /** More paths than a desktop is likely to pass to one launch, and few enough to bound a request. */
    static final int MAX_PATHS = 1024;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int ACCEPTED = 1;
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private final Path directory;
    private final Path socket;
    private final Duration timeout;

    InstanceChannel(Path directory, Duration timeout) {
        this.directory = directory;
        this.socket = directory.resolve("instance.sock");
        this.timeout = timeout;
    }

    /**
     * The channel for the current user: in {@code $XDG_RUNTIME_DIR} where there is one (and its
     * per-platform equivalent from xdg-java), otherwise under {@code ~/.cache}.
     */
    public static InstanceChannel forCurrentUser() {
        // xdg-java gives null on Linux when XDG_RUNTIME_DIR is unset, and "null\Temp" on Windows
        // when LOCALAPPDATA is, so anything that is not an absolute path falls back to the home
        // directory rather than being resolved against the working directory.
        String runtime = BaseDirectory.get(BaseDirectory.XDG_RUNTIME_DIR);
        Path base = runtime != null && Path.of(runtime).isAbsolute()
                ? Path.of(runtime)
                : Path.of(System.getProperty("user.home"), ".cache");
        return new InstanceChannel(base.resolve("net.kothar.csview"), DEFAULT_TIMEOUT);
    }

    Path socketPath() {
        return socket;
    }

    /**
     * Hands {@code paths} to a running instance.
     *
     * @return true if a running instance took them; false if there is none, or it did not answer
     *         in time, in which case the caller should open them itself
     */
    public boolean send(List<String> paths) {
        if (!Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        try {
            checkDirectory();
            try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
                 Watchdog watchdog = Watchdog.start(channel, timeout)) {

                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
                writeRequest(out, paths);
                out.flush();

                return Channels.newInputStream(channel).read() == ACCEPTED;
            }
        } catch (IOException e) {
            // Most often a socket left behind by an instance that did not exit cleanly.
            return false;
        }
    }

    /**
     * Starts accepting requests from later launches, passing each one's paths to {@code handler}
     * on a thread of its own. The handler runs off the UI thread.
     *
     * @return closes the socket and stops listening
     */
    public Closeable listen(Consumer<List<String>> handler) throws IOException {
        prepareDirectory();

        // A socket file left by an instance that crashed would make bind fail.
        Files.deleteIfExists(socket);

        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            server.bind(UnixDomainSocketAddress.of(socket));
        } catch (IOException e) {
            server.close();
            throw e;
        }
        socket.toFile().deleteOnExit();

        Thread acceptor = new Thread(() -> accept(server, handler), "csview-instance-channel");
        acceptor.setDaemon(true);
        acceptor.start();

        return () -> {
            server.close();
            Files.deleteIfExists(socket);
        };
    }

    private void accept(ServerSocketChannel server, Consumer<List<String>> handler) {
        while (server.isOpen()) {
            try {
                SocketChannel client = server.accept();
                // One thread per request, so a launch that connects and then stalls cannot hold up
                // the ones after it.
                Thread.ofVirtual().name("csview-open-request").start(() -> serve(client, handler));
            } catch (IOException e) {
                if (server.isOpen()) {
                    System.err.println("Stopped accepting files from other launches: " + e);
                }
                return;
            }
        }
    }

    private void serve(SocketChannel client, Consumer<List<String>> handler) {
        try (client; Watchdog watchdog = Watchdog.start(client, timeout)) {
            List<String> paths = readRequest(new DataInputStream(new BufferedInputStream(Channels.newInputStream(client))));
            handler.accept(paths);
            Channels.newOutputStream(client).write(ACCEPTED);
        } catch (IOException | RuntimeException e) {
            // Leaving the request unanswered makes the other launch open its own window.
            System.err.println("Ignored a request from another launch: " + e);
        }
    }

    static void writeRequest(DataOutput out, List<String> paths) throws IOException {
        if (paths.size() > MAX_PATHS) {
            throw new IOException(paths.size() + " files is more than one request may carry (" + MAX_PATHS + ")");
        }
        out.writeInt(MAGIC);
        out.writeInt(paths.size());
        for (String path : paths) {
            out.writeUTF(path);
        }
    }

    static List<String> readRequest(DataInput in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a CSView open request");
        }
        int count = in.readInt();
        if (count < 0 || count > MAX_PATHS) {
            throw new IOException("Request claims " + count + " files; the limit is " + MAX_PATHS);
        }
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paths.add(in.readUTF());
        }
        return paths;
    }

    /** Creates the socket's directory if need be, then checks it as {@link #checkDirectory} does. */
    private void prepareDirectory() throws IOException {
        Files.createDirectories(directory.getParent());
        try {
            if (Files.getFileAttributeView(directory.getParent(), PosixFileAttributeView.class) != null) {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            } else {
                Files.createDirectory(directory);
                // An elevated administrator's new files can belong to the Administrators group
                // rather than to them, which checkDirectory would refuse. The creator may always
                // give a new directory to itself.
                FileOwnerAttributeView owner = Files.getFileAttributeView(directory, FileOwnerAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (owner != null) {
                    owner.setOwner(currentUser());
                }
            }
        } catch (FileAlreadyExistsException e) {
            // Checked below.
        }
        checkDirectory();
    }

    /**
     * Refuses a socket directory that someone else could have put there or could reach into: it
     * must be a plain directory, not a link, junction or other reparse point, and belong to this
     * user. Its permissions are then narrowed to the owner alone: on Linux and macOS the mode
     * becomes rwx------, which also tightens the directory earlier versions created with the
     * default umask; on Windows the access control list becomes a single entry for the owner,
     * replacing what the directory inherited. The socket file inherits that list when it is
     * created, and Windows only lets a process connect to a Unix socket it can write to.
     */
    private void checkDirectory() throws IOException {
        // NOFOLLOW_LINKS only makes Java report symbolic links as links. A Windows junction still
        // reads as a directory, but also as "other", as every reparse point does.
        BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException(directory + " is not a plain directory");
        }

        UserPrincipal me = currentUser();

        PosixFileAttributeView posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            PosixFileAttributes posixAttributes = posix.readAttributes();
            requireOwner(posixAttributes.owner(), me);
            if (!posixAttributes.permissions().equals(OWNER_ONLY)) {
                posix.setPermissions(OWNER_ONLY);
            }
            return;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            requireOwner(acl.getOwner(), me);
            boolean onlyMe = acl.getAcl().stream()
                    .filter(entry -> entry.type() == AclEntryType.ALLOW)
                    .allMatch(entry -> entry.principal().equals(me));
            if (!onlyMe) {
                acl.setAcl(List.of(AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(me)
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                        .build()));
            }
            return;
        }

        throw new IOException("Cannot tell who may open " + directory);
    }

    private void requireOwner(UserPrincipal owner, UserPrincipal me) throws IOException {
        if (!owner.equals(me)) {
            throw new IOException(directory + " belongs to " + owner.getName() + ", not " + me.getName());
        }
    }

    /**
     * The user this process runs as. On Windows the name is qualified with its domain, so that a
     * domain account is not confused with a local account of the same name.
     */
    UserPrincipal currentUser() throws IOException {
        UserPrincipalLookupService lookup = directory.getFileSystem().getUserPrincipalLookupService();
        String name = System.getProperty("user.name");
        String domain = System.getenv("USERDOMAIN");
        if (File.separatorChar == '\\' && domain != null && !domain.isBlank()) {
            try {
                return lookup.lookupPrincipalByName(domain + "\\" + name);
            } catch (UserPrincipalNotFoundException e) {
                // Fall back to the bare name.
            }
        }
        return lookup.lookupPrincipalByName(name);
    }

    /**
     * Closes a channel if the exchange on it has not finished within the timeout. A blocking
     * channel's reads ignore socket timeouts, and closing it is what wakes them.
     */
    private record Watchdog(Thread thread) implements AutoCloseable {

        static Watchdog start(Closeable target, Duration timeout) {
            return new Watchdog(Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(timeout);
                    target.close();
                } catch (InterruptedException | IOException e) {
                    // Finished in time, or already closed.
                }
            }));
        }

        @Override
        public void close() {
            thread.interrupt();
        }
    }
}
