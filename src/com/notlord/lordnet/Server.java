package com.notlord.lordnet;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class Server extends Thread{
	private final String separatorId = "GSON_SEPARATOR_ID";
	private static final Gson gson = new Gson();
	private boolean running = false;
	private ServerSocket socket;
	private final int port;
	private final List<ServerListener> listeners = new ArrayList<>();
	private final List<ClientInstance> clients = new ArrayList<>();
	private int id = 0;

	/**
	 * creates a server.
	 * @param port port the server listens to
	 */
	public Server(int port) {
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
	}

	/**
	 * starts the server.
	 */
	@Override
	public synchronized void start() {
		super.start();
	}


	@Override
	public void run(){
		if(!running) {
			running = true;
			serverClientConnectionHandle();
		}
	}

	/**
	 * stops the server and closes all clients.
	 */
	public void stopServer() {
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
		try{
			initialize();
		}
		catch (IOException e) {e.printStackTrace();}
		while (running){
			try{
				clientConnect(new ClientInstance(this, socket.accept(), id));
				id++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		listeners.forEach(ServerListener::serverClose);
	}

	protected void clientConnect(ClientInstance clientSocket) throws IOException{
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

	public void sendAll(Object o){
		clients.forEach(clientInstance -> clientInstance.send(o));
	}

	public static class ClientInstance extends Thread{
		private final Server parentServer;
		private final Socket socket;
		public final PrintWriter writer;
		public final BufferedReader reader;
		private final int id;

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
			writer = new PrintWriter(socket.getOutputStream(), true);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}

		@Override
		public void run() {
			try {
				clientRunHandle();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		private void clientRunHandle() throws IOException, ClassNotFoundException {
			String inputLine;
			while (!socket.isClosed()){
				try {
					inputLine = reader.readLine();
				}
				catch (SocketException e) {
					if(!e.getMessage().equals("Connection reset")){
						e.printStackTrace();
					}
					break;
				}
				if (inputLine == null || ".".equals(inputLine)) {
					break;
				}
				parentServer.clientInput(this,
						gson.fromJson(inputLine.split(parentServer.separatorId)[0], Class.forName(inputLine.split(parentServer.separatorId)[1])));
			}
			parentServer.clientDisconnect(this);
			close();
		}

		/**
		 * send a packet to the client the instance is connected to.
		 * can send any object.
		 */
		public void send(Object o){
			writer.println(gson.toJson(o) + parentServer.separatorId + o.getClass().toString().split(" ")[1]);
		}

		private void close() throws IOException {
			writer.close();
			reader.close();
			socket.close();
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