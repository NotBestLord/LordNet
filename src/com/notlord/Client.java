package com.notlord;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client extends Thread{
	private final String separatorId = "GSON_SEPARATOR_ID";
	private static final Gson gson = new Gson();
	private final Socket socket;
	private final PrintWriter writer;
	private final BufferedReader reader;
	private final List<ClientListener> listeners = new ArrayList<>();
	private boolean running = true;

	/**
	 * creates a client.
	 * @param host ip/dns address of the server.
	 * @param port port of the server.
	 * @throws IOException thrown if an error occurs when creating an input/output stream.
	 */
	public Client(String host, int port) throws IOException {
		socket = new Socket(host, port);
		writer = new PrintWriter(socket.getOutputStream(), true);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	/**
	 * add listener to the client.
	 */
	public void addListener(ClientListener listener){
		listeners.add(listener);
	}

	@Override
	public void run() {
		try {
			handleClient();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	protected void handleClient() throws IOException, ClassNotFoundException {
		listeners.forEach(ClientListener::connect);
		String inputLine;
		while (!socket.isClosed()){
			try {
				inputLine = reader.readLine();
			}
			catch (Exception e) {
				if(!e.getMessage().equals("Socket closed")){
					e.printStackTrace();
				}
				break;
			}
			if(inputLine != null) {
				if (".".equals(inputLine)) {
					break;
				}
				Object o = gson.fromJson(inputLine.split(separatorId)[0], Class.forName(inputLine.split(separatorId)[1]));
				listeners.forEach(clientListener -> clientListener.receive(o));
			}
		}
		if(running) close();
	}

	/**
	 * send packet to the server.
	 * can send any object.
	 */
	public void send(Object o) {
		writer.println(gson.toJson(o) + separatorId + o.getClass().toString().split(" ")[1]);
	}

	/**
	 * close the client.
	 * @throws IOException thrown if an error occurs when closing input stream/output stream/socket.
	 */
	public void close() throws IOException {
		running = false;
		listeners.forEach(ClientListener::disconnect);
		writer.close();
		reader.close();
		socket.close();
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		Client client = new Client("localhost", 12345);
		client.addListener(new ClientListener() {
			@Override
			public void connect() {
				System.out.println("connect");
			}

			@Override
			public void disconnect() {
				System.out.println("disconnect");
			}

			@Override
			public void receive(Object o) {
				System.out.println("msg:"+o.toString());
			}
		});
		client.start();
		client.send(new ArrayList<>(List.of(1, 2, 3)));
		Thread.sleep(1000);
		client.close();
	}
}
