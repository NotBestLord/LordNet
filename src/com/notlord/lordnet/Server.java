package com.notlord.lordnet;

import com.google.gson.Gson;
import com.notlord.lordnet.listeners.ServerListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
	private final String separatorId = UUID.randomUUID() + "-sepId";
	private static final Gson gson = new Gson();
	private volatile boolean running = false;
	private ServerSocket socket;
	private int port;
	private final List<ServerListener> listeners = new ArrayList<>();
	private final List<ClientInstance> clients = new CopyOnWriteArrayList<>();
	private int id = 0;

	/**
	 * creates a server.
	 * @param port port the server listens to
	 */
	public Server(int port) {
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
	private void initialize() throws IOException {
		socket = new ServerSocket(port);
		running = true;
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
		} catch (IOException e) {
			running = false;
			e.printStackTrace();
		}
		while (running) {
			try {
				clientConnect(new ClientInstance(this, socket.accept(), id));
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
		byte[] bytes = separatorId.getBytes(StandardCharsets.UTF_8);
		clientSocket.writer.writeInt(bytes.length);
		clientSocket.writer.write(bytes);
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

	public static class ClientInstance extends Thread implements IClientInstance{
		private final Server parentServer;
		private final Socket socket;
		private final DataOutputStream writer;
		private final DataInputStream reader;
		private final int id;
		private boolean running = true;
		/**
		 * an instance of a client, on the server side.
		 * @param parentServer the server the client instance is tied to.
		 * @param socket the socket of the instance.
		 * @param id the id of the instance.
		 * @throws IOException thrown when an error with creating an input/output stream occurs.
		 */
		protected ClientInstance(Server parentServer, Socket socket, int id) throws IOException {
			this.id = id;
			this.parentServer = parentServer;
			this.socket = socket;
			writer = new DataOutputStream(socket.getOutputStream());
			reader = new DataInputStream(socket.getInputStream());
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
					if(l > 0){
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
				if(bytes != null) {
					try {
						String input = new String(bytes, StandardCharsets.UTF_8);
						Object o = gson.fromJson(input.split(parentServer.separatorId)[0], Class.forName(input.split(parentServer.separatorId)[1]));
						parentServer.clientInput(this, o);
					} catch (ClassNotFoundException ignored) {}
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
			byte[] bytes = (gson.toJson(o) + parentServer.separatorId + o.getClass().toString().split(" ")[1]).getBytes(StandardCharsets.UTF_8);
			try {
				writer.writeInt(bytes.length);
				writer.write(bytes);
			}
			catch (IOException e){
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
}
