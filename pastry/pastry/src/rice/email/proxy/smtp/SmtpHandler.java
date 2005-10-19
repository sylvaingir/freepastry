package rice.email.proxy.smtp;

import rice.email.proxy.smtp.commands.*;
import rice.email.proxy.smtp.manager.*;
import rice.email.proxy.util.*;

import java.io.IOException;

import java.net.*;

class SmtpHandler {

  // protocol and configuration global stuff
  SmtpCommandRegistry _registry;
  SmtpManager _manager;
  Workspace _workspace;

  // session stuff
  SmtpConnection _conn;
  SmtpState _state;

  // command parsing stuff
  boolean _quitting;
  String _currentLine;

  public SmtpHandler(SmtpCommandRegistry registry, SmtpManager manager, Workspace workspace) {
    _registry = registry;
    _manager = manager;
    _workspace = workspace;
  }

  public void handleConnection(Socket socket) throws IOException {
    _conn = new SmtpConnection(this, socket);
    _state = new SmtpState(_workspace);

    try {
      _quitting = false;

      sendGreetings();

      while (!_quitting) {
        handleCommand();
      }

    } catch (SocketTimeoutException ste) {
      _conn.println("421 Service shutting down and closing transmission channel");
    } catch (IOException e) {
      System.out.println("Detected connection error " + e + " - closing.");
    } finally {
      _state.clearMessage();
    }
  }

  protected void sendGreetings()
  {
    _conn.println("220 " + _conn.getServerGreetingsName() +
                  " Simple Mail Transfer Service Ready");
  }

  protected void handleCommand() throws IOException {
    _currentLine = _conn.readLine().trim();

    if (_currentLine == null) {
      quit();
      return;
    }

    // eliminate invalid line lengths before parsing
    if (! commandLegalSize()) {
      return;
    }

    String commandName = _currentLine.substring(0, 4).toUpperCase();

    SmtpCommand command = _registry.getCommand(commandName);

    if (command == null) {
      _conn.println("500 Command not recognized");
      return;
    }

    command.execute(_conn, _state, _manager, _currentLine);
  }

  private boolean commandLegalSize() {
    if (_currentLine.length() < 4) {
      _conn.println("500 Invalid command. Must be 4 characters");

      return false;
    }

    if (_currentLine.length() > 4 &&
        _currentLine.charAt(4) != ' ') {
      _conn.println("500 Invalid command. Must be 4 characters");

      return false;
    }

    if (_currentLine.length() > 1000) {
      _conn.println("500 Command too long.  1000 character maximum.");

      return false;
    }

    return true;
  }

  public void quit() {
    _quitting = true;
  }
}