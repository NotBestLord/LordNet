package com.notlord.lordnet.listeners;

public interface ClientListener {
	void connect();
	void disconnect();
	void receive(Object o);
}
