/**
 *    Copyright 2009-2015 the original author or authors.
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
 * Object 类型处理器
 * @author Clinton Begin
 */
public class ObjectTypeHandler extends BaseTypeHandler<Object> {

  /**
   * 直接调用 {@link PreparedStatement#setObject(int, Object)}
   */
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setObject(i, parameter);
  }

  /**
   * 直接调用 {@link ResultSet#getObject(String)}
   */
  @Override
  public Object getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    return rs.getObject(columnName);
  }

  /**
   * 直接调用 {@link ResultSet#getObject(int)}
   */
  @Override
  public Object getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    return rs.getObject(columnIndex);
  }

  /**
   * 直接调用 {@link CallableStatement#getObject(int)}
   */
  @Override
  public Object getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return cs.getObject(columnIndex);
  }
}
