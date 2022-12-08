package com.notlord.lordnet;

import com.google.gson.Gson;
import com.notlord.lordnet.listeners.ClientListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static com.notlord.lordnet.Utilities.fromPacketMessage;
import static com.notlord.lordnet.Utilities.toPacketMessage;

public class Client {
	private volatile String separatorId = null;
	private static final Gson gson = new Gson();
	private Socket socket;
	private PrintWriter writer;
	private BufferedReader reader;
	private final List<ClientListener> listeners = new ArrayList<>();
	private volatile boolean running = false;
	private String host;
	private int port;

	/**
	 * creates a client.
	 * @param host ip/dns address of the server.
	 * @param port port of the server.
	 */
	public Client(String host, int port) {
		this.host = host;
		this.port = port;
	}

	/**
	 * set address of target server.
	 * @param host ip/dns address of the server.
	 * @param port port of the server.
	 */
	public void setAddress(String host, int port){
		this.host = host;
		this.port = port;
	}

	/**
	 * add listener to the client.
	 */
	public void addListener(ClientListener listener){
		listeners.add(listener);
	}

	/**
	 * starts the client.
	 */
	public void start(){
		if(!running) {
			running = true;
			new Thread(this::run,"Client").start();
		}
	}

	private void run(){
		try {
			initialize();
			handleClient();
		} catch (IOException | ClassNotFoundException e) {
			if (!e.getMessage().equals("Connection refused: connect"))
				e.printStackTrace();
			running = false;
		}
	}

	private void initialize() throws IOException{
		socket = new Socket(host, port);
		writer = new PrintWriter(socket.getOutputStream(), true);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		try {
			separatorId = reader.readLine();
		}
		catch (Exception e){
			System.out.println("Failed to acquire separator Id from server");
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
				if(!e.getMessage().equals("Socket closed") && !e.getMessage().equals("Connection reset")){
					e.printStackTrace();
				}
				break;
			}
			if(inputLine != null) {
				if (".".equals(inputLine)) {
					break;
				}
				try {
					Object o = gson.fromJson(fromPacketMessage(inputLine.split(separatorId)[0]), Class.forName(inputLine.split(separatorId)[1]));
					listeners.forEach(clientListener -> clientListener.receive(o));
				}
				catch (ClassNotFoundException ignored){}
			}
		}
		if(running) close();
	}

	/**
	 * send packet to the server.
	 * can send any object.
	 */
	public void send(Object o) {
		while (separatorId == null) {
			Thread.onSpinWait();
		}
		writer.println(toPacketMessage(gson.toJson(o)) + separatorId + o.getClass().toString().split(" ")[1]);
	}

	/**
	 * close the client.
	 */
	public void close() {
		if(running) {
			running = false;
			listeners.forEach(ClientListener::disconnect);
			writer.close();
			try {
				reader.close();
			}
			catch (IOException e){
				System.out.println("Failed to close input stream.");
				e.printStackTrace();
			}
			try {
				socket.close();
			}
			catch (IOException e){
				System.out.println("Failed to close socket.");
				e.printStackTrace();
			}
		}
	}

	/**
	 * returns if the client is connected to server
	 */
	public boolean isConnected(){
		return running;
	}
}
