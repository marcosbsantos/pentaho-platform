/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License, version 2 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/gpl-2.0.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 *
 * Copyright 2006 - 2014 Pentaho Corporation.  All rights reserved.
 */
package org.pentaho.platform.engine.services.connection.datasource.dbcp;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pentaho.database.model.DatabaseAccessType;
import org.pentaho.database.model.DatabaseConnection;
import org.pentaho.database.model.IDatabaseConnection;
import org.pentaho.platform.api.engine.ICacheManager;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.ObjectFactoryException;
import org.pentaho.platform.api.repository.datasource.DatasourceMgmtServiceException;

/**
 * NonPooledDatasourceSystemListener tests
 * 
 * @author Yury Bakhmutski
 * @see NonPooledDatasourceSystemListener
 */
public class NonPooledDatasourceSystemListenerTest {

  @Mock
  NonPooledDatasourceSystemListener nonPooledDatasourceSystemListenerSpy;
  @Mock
  ICacheManager ICacheManagerMock;

  @Before
  public void init() {
    MockitoAnnotations.initMocks( this );
  }

  @Test
  public void testStartupIsSetupDataSourceForConnectionCalled() throws ObjectFactoryException,
    DatasourceMgmtServiceException {

    stubGetListOfDatabaseConnectionsMethod( DatabaseAccessType.NATIVE );
    stubIsPortUsedByServerMethod( false );
    // to avoid NullPointerException
    stubAddCacheRegionsMethod();

    // call real methods
    callRealSetupDataSourceForConnection();
    callRealStartup();

    // calling testing method
    callStartup();

    // check if setupDataSourceForConnection was called
    isSetupDataSourceForConnectionWasCalled( 1 );
  }


  @Test
  public void testStartupJNDINotCalledIsPortUsedByServer() throws ObjectFactoryException,
    DatasourceMgmtServiceException {

    stubGetListOfDatabaseConnectionsMethod( DatabaseAccessType.JNDI );
    stubIsPortUsedByServerMethod( false );
    // to avoid NullPointerException
    stubAddCacheRegionsMethod();

    // call real methods
    callRealSetupDataSourceForConnection();
    callRealStartup();

    // calling testing method
    callStartup();

    // check isPortUsedByServer wasn't called
    verify( nonPooledDatasourceSystemListenerSpy, never() ).isPortUsedByServer( any( IDatabaseConnection.class ) );
  }

  @Test
  public void testStartupIsSetupDataSourceForConnectionNotCalled() throws ObjectFactoryException,
    DatasourceMgmtServiceException {

    stubGetListOfDatabaseConnectionsMethod( DatabaseAccessType.NATIVE );
    stubIsPortUsedByServerMethod( true );
    // to avoid NullPointerException
    stubAddCacheRegionsMethod();

    // call real methods
    callRealSetupDataSourceForConnection();
    callRealStartup();

    // calling testing method
    callStartup();

    // check if setupDataSourceForConnection wasn't called
    isSetupDataSourceForConnectionWasCalled( 0 );
  }

  private void stubIsPortUsedByServerMethod( boolean returnedValue ) {
    when( nonPooledDatasourceSystemListenerSpy.isPortUsedByServer( any( IDatabaseConnection.class ) ) ).thenReturn(
        returnedValue );
  }

  private void stubAddCacheRegionsMethod() {
    when( nonPooledDatasourceSystemListenerSpy.addCacheRegions() ).thenReturn( ICacheManagerMock );
  }

  private void stubGetListOfDatabaseConnectionsMethod( DatabaseAccessType databaseAccessType )
    throws ObjectFactoryException, DatasourceMgmtServiceException {
    DatabaseConnection databaseConnection = mock( DatabaseConnection.class );
    when( databaseConnection.getAccessType() ).thenReturn( databaseAccessType );

    List<IDatabaseConnection> connectionsList = new LinkedList<IDatabaseConnection>();
    connectionsList.add( databaseConnection );
    when( nonPooledDatasourceSystemListenerSpy.getListOfDatabaseConnections( any( IPentahoSession.class ) ) )
        .thenReturn( connectionsList );
  }

  private void callStartup() {
    nonPooledDatasourceSystemListenerSpy.startup( mock( IPentahoSession.class ) );
  }

  private void callRealStartup() {
    doCallRealMethod().when( nonPooledDatasourceSystemListenerSpy ).startup( any( IPentahoSession.class ) );
  }

  private void callRealSetupDataSourceForConnection() {
    doCallRealMethod().when( nonPooledDatasourceSystemListenerSpy ).setupDataSourceForConnection(
        any( IDatabaseConnection.class ) );
  }

  private void isSetupDataSourceForConnectionWasCalled( int wishedTimes ) {
    switch ( wishedTimes ) {
      case 0:
        verify( nonPooledDatasourceSystemListenerSpy, never() ).setupDataSourceForConnection(
            any( IDatabaseConnection.class ) );
        break;
      case 1:
        verify( nonPooledDatasourceSystemListenerSpy ).setupDataSourceForConnection( any( IDatabaseConnection.class ) );
        break;
      default:
        verify( nonPooledDatasourceSystemListenerSpy, times( wishedTimes ) ).setupDataSourceForConnection(
            any( IDatabaseConnection.class ) );
        break;
    }
  }
}
