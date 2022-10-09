# LordNet
A simple Java library for networking I made. 
Made mainly for the final project in 12th grade, but can be used in other applications.
(Uses Gson, but does not require it to be added to the java project)

Coded in Java 17.0.2, may run on older versions, but is yet to be tested, use at your own discretion.


# Usage:
- For Server:
  1. Create A Server Class Object.
  2. Create And Add ServerListener Objects To The Server.
  3. Use Server.start();
  4. To Close use Server.close();

- For Client:
  1. Create A Client Class Object.
  2. Create And Add ClientListener Objects To The Server.
  3. Use Client.start();
  4. To Close use Client.close();


- Small warning: due to how Java works, immutible classes cannot be created from string, and as such cannot be sent, instead use extended classes.
  Example: List.of() returns an Immutible List and cannot be sent. Instead use new Arraylist<>(List.of()), which can be sent since arraylist is not immutible.
