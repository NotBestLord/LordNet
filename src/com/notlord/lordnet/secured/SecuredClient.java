package com.notlord.lordnet.secured;

import com.google.gson.Gson;
import com.notlord.lordnet.listeners.ClientListener;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static com.notlord.lordnet.secured.SecuredUtilities.decryptPacketMessage;
import static com.notlord.lordnet.secured.SecuredUtilities.encryptPacketMessage;

public class SecuredClient {
	private volatile String separatorId = null;
	private static final Gson gson = new Gson();
	private Socket socket;
	private DataOutputStream writer;
	private DataInputStream reader;
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private final List<ClientListener> listeners = new ArrayList<>();
	private volatile boolean running = false;
	private String host;
	private int port;

	/**
	 * creates a client.
	 * @param host ip/dns address of the server.
	 * @param port port of the server.
	 */
	public SecuredClient(String host, int port) {
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
		} catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
			if (!e.getMessage().equals("Connection refused: connect"))
				e.printStackTrace();
			running = false;
		}
	}

	private void initialize() throws IOException, NoSuchAlgorithmException {
		socket = new Socket(host, port);
		writer = new DataOutputStream(socket.getOutputStream());
		reader = new DataInputStream(socket.getInputStream());
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		privateKey = pair.getPrivate();
		try {
			writer.writeInt(pair.getPublic().getEncoded().length);
			writer.write(pair.getPublic().getEncoded());
			writer.flush();
			int l = reader.readInt();
			byte[] bytes = new byte[l];
			reader.readFully(bytes,0,l);
			separatorId = new String(bytes, StandardCharsets.UTF_8);
			l = reader.readInt();
			bytes = new byte[l];
			reader.readFully(bytes,0,l);
			publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
		}
		catch (Exception e){
			System.out.println("Failed to acquire separator Id from server");
			e.printStackTrace();
		}
	}
	protected void handleClient() throws IOException, ClassNotFoundException {
		listeners.forEach(ClientListener::connect);
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
				if(!e.getMessage().equals("Socket closed") && !e.getMessage().equals("Connection reset")){
					e.printStackTrace();
				}
				break;
			}
			if (bytes != null) {
				try {
					String decrypted = decryptPacketMessage(privateKey, bytes);
					Object o = gson.fromJson(decrypted.split(separatorId)[0], Class.forName(decrypted.split(separatorId)[1]));
					listeners.forEach(clientListener -> clientListener.receive(o));
				}
				catch (ClassNotFoundException ignored){}
				catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
					e.printStackTrace();
				}
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
		try {
			byte[] bytes = (gson.toJson(o) + separatorId + o.getClass().toString().split(" ")[1]).getBytes(StandardCharsets.UTF_8);
			bytes = encryptPacketMessage(publicKey,bytes);
			writer.writeInt(bytes.length);
			writer.write(bytes);
		} catch (NoSuchPaddingException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * close the client.
	 */
	public void close() {
		if(running) {
			running = false;
			listeners.forEach(ClientListener::disconnect);
			try {
				writer.close();
				reader.close();
			}
			catch (IOException e){
				System.out.println("Failed to close streams.");
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
