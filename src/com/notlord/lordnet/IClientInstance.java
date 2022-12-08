package com.notlord.lordnet;

public interface IClientInstance {
	void close();
	void send(Object o);
	int getID();
}
