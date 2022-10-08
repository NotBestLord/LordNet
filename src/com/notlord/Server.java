package com.notlord;

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
	private final Thread thread = new Thread(this::serverClientConnectionHandle);
	private boolean running = false;
	private ServerSocket socket;
	private final int port;
	private final List<ServerListener> listeners = new ArrayList<>();
	private final List<ClientInstance> clients = new ArrayList<>();
	private int id = 0;
	public Server(int port) {
		this.port = port;
	}
	public void addListener(ServerListener l){
		listeners.add(l);
	}
	public void initialize() throws IOException {
		socket = new ServerSocket(port);
	}
	public ServerSocket getSocket() {
		return socket;
	}

	@Override
	public void run(){
		if(!running) {
			running = true;
			thread.start();
		}
	}

	public void stopServer() throws IOException {
		if(running) {
			running = false;
			for (ClientInstance client : clients) {
				client.close();
			}
			clients.clear();
			socket.close();
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
		public ClientInstance(Server parentServer, Socket socket, int id) throws IOException {
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

		public void send(Object o){
			writer.println(gson.toJson(o) + parentServer.separatorId + o.getClass().toString().split(" ")[1]);
		}

		public void close() throws IOException {
			writer.close();
			reader.close();
			socket.close();
		}

		public int getID(){
			return id;
		}
	}


	public static void main(String[] args) {
		Server server = new Server(12345);
		server.addListener(new ServerListener() {
			@Override
			public void clientConnect(ClientInstance client) {
				System.out.println("Client Connect");
			}

			@Override
			public void clientDisconnect(ClientInstance client) {
				System.out.println("Client Disconnect");
			}

			@Override
			public void serverClose() {
				System.out.println("Server Close");
			}

			@Override
			public void clientReceive(ClientInstance client, Object o) {
				System.out.println("Client msg: "+o.toString());
				client.send(o);
			}
		});
		server.start();
	}
}
