package com.notlord.lordnet.listeners;

import com.notlord.lordnet.IClientInstance;

public interface ServerListener {
	void clientConnect(IClientInstance client);
	void clientReceive(IClientInstance client, Object o);
	void clientDisconnect(IClientInstance client);
	void serverClose();
}
