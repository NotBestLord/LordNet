package com.notlord.lordnet;

public class Utilities {
	protected static String toPacketMessage(String string){
		return string.replace("{","\\{").replace("}","\\}")
				.replace("[","\\[").replace("]","\\]").replace("$","\\$");
	}
	protected static String fromPacketMessage(String string){
		return string.replace("\\{","{").replace("\\}","}")
				.replace("\\[","[").replace("\\]","]").replace("\\$","$");
	}
}
