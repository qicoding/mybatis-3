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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型处理器
 * <p>
 *     无论是 MyBatis 在预处理语句（PreparedStatement）中设置一个参数时，还是从结果集中取出一个值时，
 *     都会用类型处理器将获取的值以合适的方式转换成 Java 类型
 * </p>
 *
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /**
   * 把 java 对象设置到 PreparedStatement 的参数中
   * @param i 从1开始
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * 从 ResultSet 中取出columnName对应数据，然后转换为 java 对象
   * @param columnName Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  /**
   * 用于从 ResultSet 中取出数据转换为 java 对象
   */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  /**
   * 用于从 CallableStatement 中取出数据转换为 java 对象
   */
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
