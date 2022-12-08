package com.notlord.lordnet.secured;

import com.google.gson.Gson;
import com.notlord.lordnet.IClientInstance;
import com.notlord.lordnet.listeners.ServerListener;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.notlord.lordnet.secured.SecuredUtilities.decryptPacketMessage;
import static com.notlord.lordnet.secured.SecuredUtilities.encryptPacketMessage;

public class SecuredServer {
	private final String separatorId = UUID.randomUUID() + "-si";
	private static final Gson gson = new Gson();
	private volatile boolean running = false;
	private ServerSocket socket;
	private int port;
	private final List<ServerListener> listeners = new ArrayList<>();
	private final List<ClientInstance> clients = new CopyOnWriteArrayList<>();
	private int id = 0;
	private PrivateKey privateKey;
	private PublicKey publicKey;

	/**
	 * creates a server.
	 * @param port port the server listens to
	 */
	public SecuredServer(int port) {
		this.port = port;
	}

	/**
	 * set port of server
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * adds a listener to the server.
	 * @param l listener to add
	 */
	public void addListener(ServerListener l){
		listeners.add(l);
	}
	private void initialize() throws IOException, NoSuchAlgorithmException {
		socket = new ServerSocket(port);
		running = true;
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		privateKey = pair.getPrivate();
		publicKey = pair.getPublic();
	}

	/**
	 * starts the server.
	 */
	public void start(){
		if(!running) {
			running = true;
			new Thread(this::serverClientConnectionHandle,"Server").start();
		}
	}

	/**
	 * stops the server and closes all clients.
	 */
	public void close() {
		if(running) {
			running = false;
			try {
				for (ClientInstance client : clients) {
					client.close();
				}
				clients.clear();
				socket.close();
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
	}

	private void serverClientConnectionHandle() {
		try {
			initialize();
		} catch (IOException | NoSuchAlgorithmException e) {
			running = false;
			e.printStackTrace();
		}
		while (running) {
			try {
				clientConnect(new ClientInstance(this,privateKey,socket.accept(), id));
				id++;
			} catch (IOException e) {
				if (!e.getMessage().equals("Socket closed"))
					e.printStackTrace();
				break;
			}
		}
		listeners.forEach(ServerListener::serverClose);
	}

	protected void clientConnect(ClientInstance clientSocket) throws IOException{
		clientSocket.writer.writeInt(separatorId.getBytes(StandardCharsets.UTF_8).length);
		clientSocket.writer.write(separatorId.getBytes(StandardCharsets.UTF_8));
		clientSocket.writer.writeInt(publicKey.getEncoded().length);
		clientSocket.writer.write(publicKey.getEncoded());
		clientSocket.start();
		clients.add(clientSocket);
		listeners.forEach((listener -> listener.clientConnect(clientSocket)));
	}

	protected void clientDisconnect(ClientInstance clientSocket){
		listeners.forEach((listener -> listener.clientDisconnect(clientSocket)));
		clients.remove(clientSocket);
	}

	protected void clientInput(ClientInstance clientSocket, Object o){
		listeners.forEach((listener -> listener.clientReceive(clientSocket, o)));
	}

	/**
	 * sends a packet to all client instances.
	 * @param o the packet
	 */
	public void sendAll(Object o){
		clients.forEach(clientInstance -> clientInstance.send(o));
	}

	/**
	 * sends a packet to all client instances but the ones specified.
	 * @param o the packet
	 * @param excludedClients the client instances that should not receive the packet.
	 */
	public void sendAllExclude(Object o, ClientInstance... excludedClients){
		List<ClientInstance> excludedInstances = new ArrayList<>(List.of(excludedClients));
		clients.forEach(clientInstance -> {
			if(!excludedInstances.contains(clientInstance)){
				clientInstance.send(o);
			}
		});
	}

	/**
	 * returns if the server is running.
	 */
	public boolean isRunning(){
		return running;
	}

	public static class ClientInstance extends Thread implements IClientInstance {
		private final SecuredServer parentServer;
		private final Socket socket;
		private final DataOutputStream writer;
		private final DataInputStream reader;
		private final PrivateKey privateKey;
		private final int id;
		private boolean running = true;
		private PublicKey publicKey;
		/**
		 * an instance of a client, on the server side.
		 * @param parentServer the server the client instance is tied to.
		 * @param socket the socket of the instance.
		 * @param id the id of the instance.
		 * @throws IOException thrown when an error with creating an input/output stream occurs.
		 */
		protected ClientInstance(SecuredServer parentServer, PrivateKey privateKey, Socket socket, int id) throws IOException {
			this.id = id;
			this.parentServer = parentServer;
			this.socket = socket;
			this.privateKey = privateKey;
			writer = new DataOutputStream(socket.getOutputStream());
			reader = new DataInputStream(socket.getInputStream());
			try {
				int l = reader.readInt();
				byte[] bytes = new byte[l];
				reader.readFully(bytes,0,l);
				publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
			}
			catch (Exception ignored) {}
		}

		@Override
		public void run() {
			clientRunHandle();
		}

		private void clientRunHandle() {
			byte[] bytes;
			while (!socket.isClosed()){
				try {
					int l = reader.readInt();
					if(l > 0) {
						bytes = new byte[l];
						reader.readFully(bytes,0,l);
					}
					else{
						bytes = null;
					}
				}
				catch (Exception e) {
					if(!e.getMessage().equals("Connection reset") && !e.getMessage().equals("Socket closed")){
						e.printStackTrace();
					}
					break;
				}
				if (bytes != null) {
					try {
						String decrypted = decryptPacketMessage(privateKey,bytes);
						Object o = gson.fromJson(decrypted.split(parentServer.separatorId)[0], Class.forName(decrypted.split(parentServer.separatorId)[1]));
						parentServer.clientInput(this, o);
					}
					catch (ClassNotFoundException ignored) {}
					catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
						e.printStackTrace();
					}
				}
			}
			parentServer.clientDisconnect(this);
			close();
		}

		/**
		 * send a packet to the client the instance is connected to.
		 * can send any object.
		 */
		public void send(Object o){
			try {
				byte[] bytes = encryptPacketMessage(publicKey,(gson.toJson(o) + parentServer.separatorId + o.getClass().toString().split(" ")[1]).getBytes(StandardCharsets.UTF_8));
				writer.writeInt(bytes.length);
				writer.write(bytes);
			} catch (NoSuchPaddingException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException | IOException e) {
				e.printStackTrace();
			}
		}

		public void close() {
			if(running) {
				try {
					writer.close();
					reader.close();
					socket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				running = false;
			}
		}

		/**
		 * get id of the instance.
		 * every instance has a unique id.
		 */
		public int getID(){
			return id;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		SecuredServer server = new SecuredServer(7777);
		server.addListener(new ServerListener() {
			@Override
			public void clientConnect(IClientInstance client) {
				System.out.println(client.getID());
			}

			@Override
			public void clientReceive(IClientInstance client, Object o) {
				System.out.println(client.getID()+":"+o);
				client.send(new ArrayList<>(List.of(1,2,3,"bruh")));
			}

			@Override
			public void clientDisconnect(IClientInstance client) {
				System.out.println("dis"+client.getID());
			}

			@Override
			public void serverClose() {
				System.out.println("closed");
			}
		});
		server.start();
		Thread.sleep(20000);
		server.close();
	}
}
