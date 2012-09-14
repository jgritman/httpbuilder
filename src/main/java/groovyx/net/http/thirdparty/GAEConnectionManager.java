/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2010 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU Lesser General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU Lesser General Public License for more details.

     You should have received a copy of the GNU Lesser General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.


     PLEASE NOTE THAT THIS FILE'S LICENSE IS DIFFERENT FROM THE REST OF ESXX!
*/

package groovyx.net.http.thirdparty;

import java.net.*;
import java.util.concurrent.TimeUnit;
import org.apache.http.conn.*;
import org.apache.http.params.*;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.*;

public class GAEConnectionManager
  implements ClientConnectionManager {

  public GAEConnectionManager() {
    SocketFactory no_socket_factory = new SocketFactory() {
    public Socket connectSocket(Socket sock, String host, int port,
                    InetAddress localAddress, int localPort,
                    HttpParams params) {
      return null;
    }

    public Socket createSocket() {
      return null;
    }

    public boolean isSecure(Socket s) {
      return false;
    }
      };

    schemeRegistry = new SchemeRegistry();
    schemeRegistry.register(new Scheme("http",  no_socket_factory, 80));
    schemeRegistry.register(new Scheme("https", no_socket_factory, 443));
  }


  public SchemeRegistry getSchemeRegistry() {
    return schemeRegistry;
  }

  public ClientConnectionRequest requestConnection(final HttpRoute route,
                                 final Object state) {
    return new ClientConnectionRequest() {
      public void abortRequest() {
    // Nothing to do
      }

      public ManagedClientConnection getConnection(long timeout, TimeUnit tunit) {
    return GAEConnectionManager.this.getConnection(route, state);
      }
    };
  }

  public void releaseConnection(ManagedClientConnection conn,
                      long validDuration, TimeUnit timeUnit) {
  }

  public void closeIdleConnections(long idletime, TimeUnit tunit) {
  }

  public void closeExpiredConnections() {
  }

  public void shutdown() {
  }

  private ManagedClientConnection getConnection(HttpRoute route, Object state) {
    return new GAEClientConnection(this, route, state);
  }

  private SchemeRegistry schemeRegistry;
}
