package com.notlord;

public interface ClientListener {
	void connect();
	void disconnect();
	void receive(Object o);
}
