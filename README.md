# LordNet
A simple Java library for networking I made. 
Made mainly for the final project in 12th grade, but can be used in other applications.
(Uses Gson, but does not require it to be added to the java project)


# Usage:
- For Server:
  1. Create A Server Class Object.
  2. Create And Add ServerListener Objects To The Server.
  3. Use Server.start();
  4. To Close use Server.stopServer();

- For Client:
  1. Create A Client Class Object.
  2. Create And Add ClientListener Objects To The Server.
  3. Use Client.start();
  4. To Close use Client.close();
