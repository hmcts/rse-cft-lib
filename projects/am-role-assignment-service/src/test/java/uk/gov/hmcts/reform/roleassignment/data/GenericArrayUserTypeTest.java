package uk.gov.hmcts.reform.roleassignment.data;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.Serializable;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.roleassignment.data.GenericArrayUserType.SQL_TYPES;

@RunWith(MockitoJUnitRunner.class)
public class GenericArrayUserTypeTest {

    @InjectMocks
    @Spy
    private GenericArrayUserType sut = new GenericArrayUserType();

    @Mock
    ResultSet resultSet;
    @Mock
    SharedSessionContractImplementor sharedSessionContractImplementor;

    @Mock
    PreparedStatement ps;

    @Mock
    Connection connection;

    @Mock
    Serializable serializable;

    @Test
    public void getStringArrayForNullSafeGet() throws SQLException {
        String[] str = new String[1];
        Object response = sut.nullSafeGet(resultSet, str, sharedSessionContractImplementor, new Object());
        assertNotNull(response);


    }

    @Test
    public void getJavaArrayForNullSafeGet() throws SQLException, IllegalAccessException, InstantiationException {
        String[] str = {"abc"};

        Array arr = getSqlArray();

        when(resultSet.getArray(str[0])).thenReturn(arr);

        Object response = sut.nullSafeGet(resultSet, str, sharedSessionContractImplementor, new Object());
        assertNotNull(response);
        assertEquals("Success Response", response);


    }

    @Test
    public void verifyPreparedStatementInNullSafeGet() throws HibernateException, SQLException {

        Object obj = null;
        sut.nullSafeSet(ps, obj, 0, sharedSessionContractImplementor);
        verify(ps).setNull(0, SQL_TYPES[0]);


    }

    @Test
    public void setArrayInStatementInNullSafeGet() throws HibernateException, SQLException {

        Array arr = getSqlArray();
        String[] str = {"Success"};

        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf("text", str)).thenReturn(arr);
        sut.nullSafeSet(ps, str, 0, sharedSessionContractImplementor);
        verify(ps).setArray(0, arr);


    }

    @Test
    public void executeAssemble() throws HibernateException {

        Object response = sut.assemble(serializable, new Object());
        assertNotNull(response);
    }

    @Test
    public void executeDeepCopy() throws HibernateException {

        Object response = sut.deepCopy(new Object());
        assertNotNull(response);
    }

    @Test
    public void executeEquals() throws HibernateException {

        String str1 = "test";
        String str2 = "test";
        assertTrue(sut.equals(str1, str2));
        assertEquals(str1.equals(str2), sut.equals(str1, str2));

        str2 = "tester";
        assertFalse(sut.equals(str1, str2));
        assertEquals(str1.equals(str2), sut.equals(str1, str2));
        assertNotNull(str1);
        assertNotNull(str2);
    }

    @Test
    public void executeDissasenble() throws HibernateException {

        String str1 = "test";
        String str2 = "test";
        Serializable result = sut.disassemble(str1);
        assertNotNull(result);
    }

    @Test
    public void executeEqualsWithNull() throws HibernateException {

        assertTrue(sut.equals(null, null));
    }

    @Test
    public void executeHashCodel() throws HibernateException {

        String str1 = "test";
        Integer response = sut.hashCode(str1);
        assertNotNull(response);
        assertNotEquals(0, response);

    }

    @Test
    public void executeMutable() throws HibernateException {


        assertTrue(sut.isMutable());
    }

    @Test
    public void executeReplace() throws HibernateException {

        Object original = new Object();
        Object response = sut.replace(original, new Object(), new Object());
        assertNotNull(response);
        assertEquals(original, response);
    }

    @Test
    public void executeSqlTypes() {


        int[] response = sut.sqlTypes();
        assertNotNull(response);
        assertEquals(2003, response[0]);

    }


    @NotNull
    private Array getSqlArray() {
        return new Array() {
            @Override
            public String getBaseTypeName() throws SQLException {
                return null;
            }

            @Override
            public int getBaseType() throws SQLException {
                return 0;
            }

            @Override
            public Object getArray() throws SQLException {
                return "Success Response";
            }

            @Override
            public Object getArray(Map<String, Class<?>> map) throws SQLException {
                return null;
            }

            @Override
            public Object getArray(long l, int i) throws SQLException {
                return null;
            }

            @Override
            public Object getArray(long l, int i, Map<String, Class<?>> map) throws SQLException {
                return null;
            }

            @Override
            public ResultSet getResultSet() throws SQLException {
                return null;
            }

            @Override
            public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
                return null;
            }

            @Override
            public ResultSet getResultSet(long l, int i) throws SQLException {
                return null;
            }

            @Override
            public ResultSet getResultSet(long l, int i, Map<String, Class<?>> map) throws SQLException {
                return null;
            }

            @Override
            public void free() throws SQLException {

            }
        };
    }
}


