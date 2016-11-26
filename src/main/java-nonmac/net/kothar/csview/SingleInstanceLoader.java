package net.kothar.csview;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.freedesktop.BaseDirectory;

public class SingleInstanceLoader implements ApplicationActions {

	private static String runtimeDir = BaseDirectory.get(BaseDirectory.XDG_RUNTIME_DIR);
	private static String portfile = runtimeDir + "/net.kothar.csview/open.port";

	private int openDocuments = 0;
	
	private Display display = Display.getDefault();

	public static void main(String[] args) throws IOException {
		if (tryOpen(args)) {
			System.out.println("Opened in single process");
			return;
		}

		new SingleInstanceLoader().start(args);
	}

	private void start(String[] args) {
		Thread listener = new Thread(this::listen);
		listener.setDaemon(true);
		listener.start();

		if (open(args)) {
			while (!display.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
		}
	}

	private boolean open(String[] args) {
		System.out.println("Open " + Arrays.asList(args));
		
		if (args.length == 0) {
			if (!new Commands(this).openFile()) {
				if (openDocuments == 0) {
					return false;
				}
			}
		} else {
			display.asyncExec(() -> {
				for (String string: args) {
					File file = new File(string);
					if (file.exists() && file.isFile()) {
						openFile(file);
					}
				}
			});
		}
		
		return true;
	}

	private static boolean tryOpen(String[] args) {
		if (!new File(portfile).exists()) {
			return false;
		}

		try (DataInputStream stream = new DataInputStream(new FileInputStream(portfile))) {
			int port = stream.readInt();

			try (Socket socket = new Socket("127.0.0.1", port)) {
				ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
				DataInputStream input = new DataInputStream(socket.getInputStream());

				output.writeObject(args);
				int read = input.read();
				if (read == 1) {
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	private void listen() {

		try (ServerSocket server = new ServerSocket()) {
			server.bind(new InetSocketAddress("127.0.0.1", 0));

			File file = new File(portfile);
			file.getParentFile().mkdirs();
			file.deleteOnExit();
			try (DataOutputStream portStream = new DataOutputStream(new FileOutputStream(portfile))) {
				portStream.writeInt(server.getLocalPort());
			}

			// Listen for new connections
			for (;;) {
				try (Socket socket = server.accept()) {
					ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());

					Object request = input.readObject();
					if (request instanceof String[]) {
						open((String[]) request);
						output.write(1);
					} else {
						output.write(0);
					}

					input.read();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void openFile(File file) {
		openDocuments++;
		
		CSView csView = new CSView(file);

		csView.addMenuBar();
		csView.open();
		csView.getShell().forceActive();

		Menu menu = csView.getMenuBarManager().getMenu();
		new Menus(this, csView, menu);

		csView.getShell().addDisposeListener(e -> {
			if (--openDocuments == 0) {
				e.display.dispose();
			}
		});
	}

}
