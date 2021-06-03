/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * 结果集包装器
 * @author Iwao AVE!
 */
public class ResultSetWrapper {

  private final ResultSet resultSet;
  /** 类型处理器注册表 */
  private final TypeHandlerRegistry typeHandlerRegistry;
  /** 列名列表 */
  private final List<String> columnNames = new ArrayList<>();
  /** 列名的类名列表 */
  private final List<String> classNames = new ArrayList<>();
  /** 列对应jdbc类型列表 */
  private final List<JdbcType> jdbcTypes = new ArrayList<>();
  /** 类型处理器map */
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<>();
  /** 已映射列名集合 key 为 结果标签ID拼接列前缀 value为列名列表 */
  private final Map<String, List<String>> mappedColumnNamesMap = new HashMap<>();
  /** 未映射列名集合 key 为 结果标签ID拼接列前缀 value为列名列表 */
  private final Map<String, List<String>> unMappedColumnNamesMap = new HashMap<>();

  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.resultSet = rs;
    final ResultSetMetaData metaData = rs.getMetaData();
    final int columnCount = metaData.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  public List<String> getColumnNames() {
    return this.columnNames;
  }

  public List<String> getClassNames() {
    return Collections.unmodifiableList(classNames);
  }

  public List<JdbcType> getJdbcTypes() {
    return jdbcTypes;
  }

  /**
   * 根据列名获取jdbc类型
   * @param columnName
   * @return
   */
  public JdbcType getJdbcType(String columnName) {
    for (int i = 0; i < columnNames.size(); i++) {
      if (columnNames.get(i).equalsIgnoreCase(columnName)) {
        return jdbcTypes.get(i);
      }
    }
    return null;
  }

  /**
   * 获取读取结果集时要使用的类型处理器。
   * 尝试通过搜索属性类型从 TypeHandlerRegistry 中获取。
   * 如果未找到，它将获取列 JDBC 类型并尝试为其获取处理程序。
   *
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   *
   * @param propertyType
   *          the property type
   * @param columnName
   *          the column name
   * @return the type handler
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;
    // 从类型处理器map中 根据列名获取类型处理器
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    // 无则需要new一个hashMap放到类型处理器map中
    if (columnHandlers == null) {
      columnHandlers = new HashMap<>();
      typeHandlerMap.put(columnName, columnHandlers);
      // 有直接获取handler
    } else {
      handler = columnHandlers.get(propertyType);
    }
    if (handler == null) {
      JdbcType jdbcType = getJdbcType(columnName);
      handler = typeHandlerRegistry.getTypeHandler(propertyType, jdbcType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      // 类型处理器为null或者未知类型处理器 尝试通过搜索属性类型从 TypeHandlerRegistry 中获取
      if (handler == null || handler instanceof UnknownTypeHandler) {
        final int index = columnNames.indexOf(columnName);
        // 通过类名反射出java的class
        final Class<?> javaType = resolveClass(classNames.get(index));
        // 如果java类型不为空并且jdbc类型也不为空 则通过类型处理器注册器获取类型处理器
        if (javaType != null && jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
          // 通过java类型获取
        } else if (javaType != null) {
          handler = typeHandlerRegistry.getTypeHandler(javaType);
          // 通过jdbc类型过去
        } else if (jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }
      // 类型处理器为null或者未知类型处理器 则创建一个对象类型处理器
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = new ObjectTypeHandler();
      }
      columnHandlers.put(propertyType, handler);
    }
    return handler;
  }

  /**
   * 通过类名反射出class
   * @param className
   * @return
   */
  private Class<?> resolveClass(String className) {
    try {
      // #699 className could be null
      if (className != null) {
        return Resources.classForName(className);
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return null;
  }

  /**
   * 加载映射和未映射的列名
   * @param resultMap Mapper.xml的resultMap标签信息封装类对象
   * @param columnPrefix 列前缀
   * @throws SQLException
   */
  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    // 已映射列名
    List<String> mappedColumnNames = new ArrayList<>();
    // 未映射列名
    List<String> unmappedColumnNames = new ArrayList<>();
    // 列名前缀转大写
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    // 批量给已映射列名添加前缀
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);
    // 遍历所有列名
    for (String columnName : columnNames) {
      // 所有列名转大写
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      // 判断已映射列名Set中是否包含列名 如果包含则追加到已映射列名列表 否则 追加到未映射列名列表中
      if (mappedColumns.contains(upperColumnName)) {
        mappedColumnNames.add(upperColumnName);
      } else {
        unmappedColumnNames.add(columnName);
      }
    }
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  /**
   * 获取已映射列名
   * @param resultMap Mapper.xml的resultMap标签信息封装类对象
   * @param columnPrefix 列前缀
   * @return
   * @throws SQLException
   */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    // 已映射列名如果为null 则再次出发加载一次 如果有加载 必然不会为null
    if (mappedColumnNames == null) {
      // 加载映射和未映射的列名
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      // 再次获取已映射列名
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

  /**
   * 获取未映射的列名
   * @param resultMap Mapper.xml的resultMap标签信息封装类对象
   * @param columnPrefix 列前缀
   * @return
   * @throws SQLException
   */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    // 未映射列名如果为null 则再次出发加载一次 如果有加载 必然不会为null
    if (unMappedColumnNames == null) {
      // 加载映射和未映射的列名
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      // 再次获取未映射列名
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return unMappedColumnNames;
  }

  /**
   * 结果标签ID 拼接列前缀 作为map的key
   * @param resultMap Mapper.xml的resultMap标签信息封装类对象
   * @param columnPrefix 列前缀
   * @return
   */
  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  /**
   * 给列名集合统一添加前缀
   * @param columnNames
   * @param prefix
   * @return
   */
  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
    // 列名集合没有 或者 前缀没有 则直接返回
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    final Set<String> prefixed = new HashSet<>();
    // 遍历列名并添加前缀
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }

}
