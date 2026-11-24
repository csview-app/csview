package net.kothar.csview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InstanceChannelTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private Path base;
    private Path directory;
    private InstanceChannel channel;
    private final BlockingQueue<List<String>> received = new LinkedBlockingQueue<>();
    private final List<Closeable> toClose = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        // Kept short: a Unix socket path is limited to around 100 bytes.
        base = Files.createTempDirectory("ic");
        directory = base.resolve("csview");
        channel = new InstanceChannel(directory, TIMEOUT);
    }

    @After
    public void tearDown() throws IOException {
        for (Closeable closeable : toClose) {
            closeable.close();
        }
        try (var paths = Files.walk(base)) {
            paths.sorted(Collections.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private void listen() throws IOException {
        toClose.add(channel.listen(received::add));
    }

    @Test
    public void handsPathsToTheRunningInstance() throws Exception {
        listen();

        List<String> paths = List.of("/data/first.csv", "/data/naïve \u2603 second.tsv");
        assertTrue(channel.send(paths));
        assertEquals(paths, received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    public void handsOverALaunchWithNoFiles() throws Exception {
        listen();

        assertTrue(channel.send(List.of()));
        assertEquals(List.of(), received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    public void reportsNoInstanceWhenNothingIsListening() {
        assertFalse(channel.send(List.of("/data/first.csv")));
    }

    @Test
    public void reportsNoInstanceForASocketLeftByACrash() throws Exception {
        Files.createDirectories(directory);
        ServerSocketChannel crashed = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        crashed.bind(UnixDomainSocketAddress.of(channel.socketPath()));
        crashed.close();
        assertTrue(Files.exists(channel.socketPath()));

        assertFalse(channel.send(List.of("/data/first.csv")));

        // And the next instance to start takes the socket over.
        listen();
        assertTrue(channel.send(List.of("/data/first.csv")));
    }

    @Test
    public void refusesMoreFilesThanARequestMayCarry() throws Exception {
        listen();

        List<String> tooMany = Collections.nCopies(InstanceChannel.MAX_PATHS + 1, "/data/first.csv");
        assertFalse(channel.send(tooMany));
        assertNull(received.poll(TIMEOUT.toMillis() * 2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void ignoresAnythingThatIsNotARequestAndKeepsListening() throws Exception {
        listen();

        try (SocketChannel stranger = SocketChannel.open(UnixDomainSocketAddress.of(channel.socketPath()))) {
            stranger.write(ByteBuffer.wrap("GET / HTTP/1.1\r\n\r\n".getBytes()));
            ByteBuffer reply = ByteBuffer.allocate(1);
            assertEquals("connection should close unanswered", -1, stranger.read(reply));
        }
        assertNull(received.poll(100, TimeUnit.MILLISECONDS));

        assertTrue(channel.send(List.of("/data/first.csv")));
        assertEquals(List.of("/data/first.csv"), received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    public void aStalledLaunchDoesNotHoldUpTheNext() throws Exception {
        listen();

        try (SocketChannel stalled = SocketChannel.open(UnixDomainSocketAddress.of(channel.socketPath()))) {
            long start = System.nanoTime();
            assertTrue(channel.send(List.of("/data/first.csv")));
            assertTrue("the second launch waited on the first",
                    System.nanoTime() - start < TIMEOUT.toNanos());
        }
    }

    @Test
    public void givesUpOnAnInstanceThatNeverAnswers() throws Exception {
        Files.createDirectories(directory);
        try (ServerSocketChannel silent = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            silent.bind(UnixDomainSocketAddress.of(channel.socketPath()));

            long start = System.nanoTime();
            assertFalse(channel.send(List.of("/data/first.csv")));
            assertTrue("send should give up after its timeout",
                    System.nanoTime() - start < TIMEOUT.toNanos() * 4);
        }
    }

    @Test
    public void keepsTheSocketDirectoryToItsOwner() throws Exception {
        assumeNotNull(Files.getFileAttributeView(base, PosixFileAttributeView.class));

        // Earlier versions created the directory with the default umask.
        Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        listen();

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
    }

    @Test
    public void refusesASocketDirectoryThatIsALink() throws Exception {
        Path elsewhere = Files.createDirectory(base.resolve("elsewhere"));
        try {
            Files.createSymbolicLink(directory, elsewhere);
        } catch (FileSystemException | UnsupportedOperationException e) {
            // Windows only lets administrators, or anyone in Developer Mode, make symbolic links.
            assumeTrue("cannot create a symbolic link here: " + e, false);
        }

        assertRefused(elsewhere);
    }

    @Test
    public void refusesASocketDirectoryThatIsAJunction() throws Exception {
        assumeTrue(isWindows());

        // Java can read junctions but not make them; any user may make one with mklink.
        Path elsewhere = Files.createDirectory(base.resolve("elsewhere"));
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", directory.toString(), elsewhere.toString())
                .redirectErrorStream(true).start();
        mklink.getInputStream().readAllBytes();
        assumeTrue("mklink /J failed", mklink.waitFor() == 0);

        assertRefused(elsewhere);
    }

    private void assertRefused(Path elsewhere) {
        try {
            listen();
            throw new AssertionError("listened through " + directory + ", which leads to " + elsewhere);
        } catch (IOException expected) {
        }
        assertFalse(channel.send(List.of("/data/first.csv")));
        assertFalse(Files.exists(elsewhere.resolve("instance.sock")));
    }

    @Test
    public void keepsTheSocketDirectoryToItsOwnerOnWindows() throws Exception {
        assumeNotNull(Files.getFileAttributeView(base, AclFileAttributeView.class));
        assumeTrue(Files.getFileAttributeView(base, PosixFileAttributeView.class) == null);

        UserPrincipal me = channel.currentUser();

        // A directory made without the channel inherits its parent's list, which in a profile
        // usually also admits SYSTEM and Administrators.
        Files.createDirectory(directory);
        listen();

        assertEquals(me, Files.getOwner(directory));
        assertOnlyAllows(me, directory);
        // The socket inherits the directory's list when it is created.
        assertOnlyAllows(me, channel.socketPath());
        assertTrue(channel.send(List.of("C:\\data\\first.csv")));
    }

    @Test
    public void rewritesAnEmptyListOnWindows() throws Exception {
        assumeNotNull(Files.getFileAttributeView(base, AclFileAttributeView.class));
        assumeTrue(Files.getFileAttributeView(base, PosixFileAttributeView.class) == null);

        UserPrincipal me = channel.currentUser();

        // Java reads a null DACL, which admits everyone, as this same empty list.
        Files.createDirectory(directory);
        Files.getFileAttributeView(directory, AclFileAttributeView.class).setAcl(List.of());
        listen();

        assertOnlyAllows(me, directory);
        assertTrue(channel.send(List.of("C:\\data\\first.csv")));
    }

    private static void assertOnlyAllows(UserPrincipal me, Path path) throws IOException {
        List<AclEntry> acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).getAcl();
        assertTrue(path + " has no access control entries, so it may admit everyone",
                acl.stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW && entry.principal().equals(me)));
        for (AclEntry entry : acl) {
            if (entry.type() == AclEntryType.ALLOW) {
                assertEquals(path + " admits " + entry.principal().getName(), me, entry.principal());
            }
        }
    }

    @Test
    public void fallsBackWhenTheSocketPathIsTooLong() throws Exception {
        // Unix socket paths are limited to about 104 bytes on macOS and 108 on Linux and Windows.
        InstanceChannel deep = new InstanceChannel(base.resolve("x".repeat(120)), TIMEOUT);
        try {
            toClose.add(deep.listen(received::add));
            throw new AssertionError("bound a socket at " + deep.socketPath());
        } catch (IOException expected) {
        }
        assertFalse(deep.send(List.of("/data/first.csv")));
    }

    @Test
    public void worksOrFallsBackInADirectoryWithANonAsciiName() throws Exception {
        // A user name such as José or 田中 puts characters like these in the socket's path. How
        // the path is encoded for the socket varies by platform; either it works, or listening
        // fails cleanly and later launches open their own windows.
        InstanceChannel accented = new InstanceChannel(base.resolve("José-田中"), TIMEOUT);
        boolean listening;
        try {
            toClose.add(accented.listen(received::add));
            listening = true;
        } catch (IOException e) {
            listening = false;
        }
        assertEquals(listening, accented.send(List.of("/data/first.csv")));
        if (listening) {
            assertEquals(List.of("/data/first.csv"), received.poll(5, TimeUnit.SECONDS));
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    @Test
    public void readsBackWhatItWrites() throws IOException {
        List<String> paths = List.of("C:\\Users\\me\\data.csv", "", "/tmp/x".repeat(1000));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InstanceChannel.writeRequest(new DataOutputStream(bytes), paths);

        assertEquals(paths, InstanceChannel.readRequest(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test(expected = IOException.class)
    public void rejectsANegativeCount() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(InstanceChannel.MAGIC);
        out.writeInt(-1);

        InstanceChannel.readRequest(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test(expected = IOException.class)
    public void rejectsACountOverTheLimit() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(InstanceChannel.MAGIC);
        out.writeInt(Integer.MAX_VALUE);

        InstanceChannel.readRequest(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
