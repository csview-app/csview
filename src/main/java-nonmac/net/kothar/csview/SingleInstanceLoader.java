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
import org.freedesktop.BaseDirectory;

public class SingleInstanceLoader {

	private static String cacheDir = BaseDirectory.get(BaseDirectory.XDG_DATA_HOME);
	private static String portfile = cacheDir + "/net.kothar.csview/open.port";
	
	static int openDocuments = 0;

	public static void main(String[] args) throws IOException {
		if (tryOpen(args)) {
			System.out.println("Opened in single process");
			return;
		}

		Thread listener = new Thread(SingleInstanceLoader::listen);
		listener.setDaemon(true);
		listener.start();
		
		Display display = open(args);
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	private static Display open(String[] args) {
		System.out.println("Open " + Arrays.asList(args));

		openDocuments++;
		Display display = Display.getDefault();
		display.asyncExec(() -> {
			CSView csView = new CSView(args);
			csView.open();
			csView.getShell().forceActive();

			csView.getShell().addDisposeListener(e -> {
				if (--openDocuments == 0) {
					e.display.dispose();
				}
			});
		});

		return display;
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

	private static void listen() {
		
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

}
