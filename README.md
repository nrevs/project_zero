# project_zero - N2P  ***!DOES NOT RUN YET!***

N2P is a stub project aspiring to be a toy I2P inspired router running directly on top of HTTPS, as opposed to I2P, which runs directly on top of TCP and UDP. The current status of the project is that it starts a Server and a Client and offers two handlers, a ServerHandler and a ClientHandler that need to be wired up to process the incoming and outgoing connections. To have a little fun, it runs a little demo if you type "dummy" in the command arguments and it can send some information to a database you can start with docker (in the resources folder) and and print the info when you're done. There is a long way to go, but baby steps.

Some things to keep in mind:
- Uses BouncyCastle and requires setting up the BouncyCastle provider (annoying!)
  (please see this guide: 
- Look in the runScript.sh for how to start the project. It is complicated by the inclusion of signed BouncyCastle jars. They're needed for generating the Certificates :P

<pre>
  Useful I2P links
  <a href="https://geti2p.net/en/about/intro">I2P Intro</a>
  <a href="https://geti2p.net/en/docs/protocol">Protocol Stack</a>
  <a href="http://www.i2p.to/spec">Specification Documents</a>
  <a href="https://geti2p.net/en/docs/how/network-database">The Network DB</a>
  <a href="https://geti2p.net/en/docs/how/tunnel-routing">Tunnels Overview</a>
  <a href="https://geti2p.net/en/docs/tunnels/implementation">Tunnels Implementation</a>
  <a href="https://geti2p.net/spec/tunnel-creation">Tunnel Creation</a>
  <a href="https://geti2p.net/spec/i2np">I2P Network Protocol (I2NP)</a>
  <a href="https://geti2p.net/en/docs/ports">Ports Used by I2P</a>
  <a href="https://github.com/i2p/i2p.i2p">Github Repo</a>
</pre>


## Features
- [] Https Server and Client with ServerHandler and ClientHandler to "handle" connections.
