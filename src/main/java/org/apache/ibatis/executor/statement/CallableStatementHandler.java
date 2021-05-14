/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;

/**
 * 支持回调的语句处理器
 * @author Clinton Begin
 */
public class CallableStatementHandler extends BaseStatementHandler {

  public CallableStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    // 执行
    cs.execute();
    // 获取更新数量
    int rows = cs.getUpdateCount();
    // 获取参数对象
    Object parameterObject = boundSql.getParameterObject();
    // 获取主键生成器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    // 把返回的主键设置到参数对象中
    keyGenerator.processAfter(executor, mappedStatement, cs, parameterObject);
    // 处理输出参数 -- 回调
    resultSetHandler.handleOutputParameters(cs);
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.addBatch();
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.execute();
    List<E> resultList = resultSetHandler.handleResultSets(cs);
    // 处理输出参数
    resultSetHandler.handleOutputParameters(cs);
    return resultList;
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.execute();
    Cursor<E> resultList = resultSetHandler.handleCursorResultSets(cs);
    // 处理输出参数
    resultSetHandler.handleOutputParameters(cs);
    return resultList;
  }

  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    String sql = boundSql.getSql();
    // 默认结果集类型
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.prepareCall(sql);
    } else { // 否则创建支持并发读的可回调Statement 该可回调Statement不支持update操作
      return connection.prepareCall(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  /**
   * 为语句设置参数
   * @param statement 语句
   * @throws SQLException
   */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    registerOutputParameters((CallableStatement) statement);
    parameterHandler.setParameters((CallableStatement) statement);
  }

  /**
   * 注册输出参数
   * @param cs
   * @throws SQLException
   */
  private void registerOutputParameters(CallableStatement cs) throws SQLException {
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 遍历参数映射
    for (int i = 0, n = parameterMappings.size(); i < n; i++) {
      ParameterMapping parameterMapping = parameterMappings.get(i);
      // 如果是输出参数
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        // 参数不含jdbcType则抛出执行异常
        if (null == parameterMapping.getJdbcType()) {
          throw new ExecutorException("The JDBC Type must be specified for output parameter.  Parameter: " + parameterMapping.getProperty());
        } else {
          // 如果数值的范围不为空并且jdbc类型数字类型
          if (parameterMapping.getNumericScale() != null && (parameterMapping.getJdbcType() == JdbcType.NUMERIC || parameterMapping.getJdbcType() == JdbcType.DECIMAL)) {
            cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE, parameterMapping.getNumericScale());
          } else {
            // 如果jdbc类型名称为空
            if (parameterMapping.getJdbcTypeName() == null) {
              cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE);
            } else { // 如果jdbc类型名称不为空空
              cs.registerOutParameter(i + 1, parameterMapping.getJdbcType().TYPE_CODE, parameterMapping.getJdbcTypeName());
            }
          }
        }
      }
    }
  }

}
