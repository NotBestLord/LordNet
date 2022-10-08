package com.notlord;

public interface ServerListener {

	void clientConnect(Server.ClientInstance client);
	void clientReceive(Server.ClientInstance client, Object o);
	void clientDisconnect(Server.ClientInstance client);
	void serverClose();
}
