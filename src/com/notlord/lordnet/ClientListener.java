package com.notlord.lordnet;

public interface ClientListener {
	void connect();
	void disconnect();
	void receive(Object o);
}
