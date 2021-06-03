/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

/**
 * 未知类型处理器
 * <p>在{@link BaseTypeHandler}的抽象方法中根据返回的结果集提供的列去获取对应的TypeHandler时候，
 *    在获取不到的情况下，就会使用{@link ObjectTypeHandler}处理</p>
 * @author Clinton Begin
 */
public class UnknownTypeHandler extends BaseTypeHandler<Object> {

  private static final ObjectTypeHandler OBJECT_TYPE_HANDLER = new ObjectTypeHandler();
  // TODO Rename to 'configuration' after removing the 'configuration' property(deprecated property) on parent class
  private final Configuration config;
  private final Supplier<TypeHandlerRegistry> typeHandlerRegistrySupplier;

  /**
   * The constructor that pass a MyBatis configuration.
   *
   * @param configuration a MyBatis configuration
   * @since 3.5.4
   */
  public UnknownTypeHandler(Configuration configuration) {
    this.config = configuration;
    this.typeHandlerRegistrySupplier = configuration::getTypeHandlerRegistry;
  }

  /**
   * The constructor that pass the type handler registry.
   *
   * @param typeHandlerRegistry a type handler registry
   * @deprecated Since 3.5.4, please use the {@link #UnknownTypeHandler(Configuration)}.
   */
  @Deprecated
  public UnknownTypeHandler(TypeHandlerRegistry typeHandlerRegistry) {
    this.config = new Configuration();
    this.typeHandlerRegistrySupplier = () -> typeHandlerRegistry;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    TypeHandler handler = resolveTypeHandler(parameter, jdbcType);
    handler.setParameter(ps, i, parameter, jdbcType);
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    TypeHandler<?> handler = resolveTypeHandler(rs, columnName);
    return handler.getResult(rs, columnName);
  }

  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    TypeHandler<?> handler = resolveTypeHandler(rs.getMetaData(), columnIndex);
    if (handler == null || handler instanceof UnknownTypeHandler) {
      handler = OBJECT_TYPE_HANDLER;
    }
    return handler.getResult(rs, columnIndex);
  }

  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return cs.getObject(columnIndex);
  }

  /**
   * 解析获取类型处理器
   * <p>根据 {@code parameter} 和 {@code jdbcType} 获取typeHandler实例</p>
   * @return {@code parameter} 为null 或者
   *    获取不了对应{@code parameter} 和 {@code jdbcType} 的typeHanlder实例
   *    或者为typeHandler实例都为{@link UnknownTypeHandler}的实例的话，都会返回{@link #OBJECT_TYPE_HANDLER}
   */
  private TypeHandler<?> resolveTypeHandler(Object parameter, JdbcType jdbcType) {
    TypeHandler<?> handler;
    if (parameter == null) {
      handler = OBJECT_TYPE_HANDLER;
    } else {
      handler = typeHandlerRegistrySupplier.get().getTypeHandler(parameter.getClass(), jdbcType);
      // check if handler is null (issue #270)
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = OBJECT_TYPE_HANDLER;
      }
    }
    return handler;
  }

  /**
   * 解析获取TypeHandler实例
   * <p>
   *     通过{@code rs}拿到结果集的描述信息{@link ResultSetMetaData}，根据描述信息去拿到列名对应的
   *     位置，再调用{@link #resolveTypeHandler(ResultSetMetaData, Integer)}获取对应的TypeHandler实例。
   * </p>
   * @param rs 结果集
   * @param column 列名
   * @return {@link TypeHandler} 实例；
   *    当 {@code handler} 为null或者 {@code handler} 为 {@link UnknownTypeHandler} 就会返回 {@link #OBJECT_TYPE_HANDLER}
   */
  private TypeHandler<?> resolveTypeHandler(ResultSet rs, String column) {
    try {
      // 将所有列的列名，对应的位置都拿出来放到columnIndexLookup中
      Map<String,Integer> columnIndexLookup;
      columnIndexLookup = new HashMap<>();
      ResultSetMetaData rsmd = rs.getMetaData();
      int count = rsmd.getColumnCount();
      boolean useColumnLabel = config.isUseColumnLabel();
      for (int i = 1; i <= count; i++) {
        String name = useColumnLabel ? rsmd.getColumnLabel(i) : rsmd.getColumnName(i);
        columnIndexLookup.put(name,i);
      }
      Integer columnIndex = columnIndexLookup.get(column);
      TypeHandler<?> handler = null;
      if (columnIndex != null) {
        handler = resolveTypeHandler(rsmd, columnIndex);
      }
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = OBJECT_TYPE_HANDLER;
      }
      return handler;
    } catch (SQLException e) {
      throw new TypeException("Error determining JDBC type for column " + column + ".  Cause: " + e, e);
    }
  }

  /**
   * 解析获取TypeHandler实例
   * @param rsmd 结果集描述信息
   * @param columnIndex 指定的列位置
   * @return TypeHandler实例
   */
  private TypeHandler<?> resolveTypeHandler(ResultSetMetaData rsmd, Integer columnIndex) {
    TypeHandler<?> handler = null;
    // 从columnIndex中获取jdbcType，不会抛出异常，但是会返回null
    JdbcType jdbcType = safeGetJdbcTypeForColumn(rsmd, columnIndex);
    // 从columnIndex中获取javaType，不会抛出异常，但是会返回null
    Class<?> javaType = safeGetClassForColumn(rsmd, columnIndex);
    // 根据现有的条件，尽可能的找到对应的typeHandler实例
    if (javaType != null && jdbcType != null) {
      handler = typeHandlerRegistrySupplier.get().getTypeHandler(javaType, jdbcType);
    } else if (javaType != null) {
      handler = typeHandlerRegistrySupplier.get().getTypeHandler(javaType);
    } else if (jdbcType != null) {
      handler = typeHandlerRegistrySupplier.get().getTypeHandler(jdbcType);
    }
    return handler;
  }

  /**
   * 安全的从列中拿到jdbcType,这里的安全应该是指捕捉的所有获取过程中抛出的异常。
   * @param rsmd 结果集的描述信息
   * @param columnIndex 指定的列位置
   * @return {@link JdbcType}，在获取过程中抛出异常会返回 {@code null}
   */
  private JdbcType safeGetJdbcTypeForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      return JdbcType.forCode(rsmd.getColumnType(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 安全的从列中拿到javaType，这里的安全应该是指捕捉的所有获取过程中抛出的异常。
   * @param rsmd 结果集的描述信息
   * @param columnIndex 指定的列位置
   * @return {@link Class}，在获取过程中抛出异常会返回 {@code null}
   */
  private Class<?> safeGetClassForColumn(ResultSetMetaData rsmd, Integer columnIndex) {
    try {
      return Resources.classForName(rsmd.getColumnClassName(columnIndex));
    } catch (Exception e) {
      return null;
    }
  }
}
