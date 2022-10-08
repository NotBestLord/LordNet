package com.notlord.lordnet;

public interface ServerListener {

	void clientConnect(Server.ClientInstance client);
	void clientReceive(Server.ClientInstance client, Object o);
	void clientDisconnect(Server.ClientInstance client);
	void serverClose();
}
