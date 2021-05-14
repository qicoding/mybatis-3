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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 简单语句处理器
 * @author Clinton Begin
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 执行更新（含插入、修改、删除）
   * @param statement
   * @return 返回更新的数量
   * @throws SQLException
   */
  @Override
  public int update(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    // 获取参数对象
    Object parameterObject = boundSql.getParameterObject();
    // 获取主键生成器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    // 如果是数据库自增的主键生成器
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // 执行SQL 并且返回数据库生成的主键
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      // 获取更新的数量
      rows = statement.getUpdateCount();
      // 把自增的主键设置到参数对象中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) { // 如果是通过selectKey方式获取的主键
      // 执行SQL
      statement.execute(sql);
      // 获取更新的数量
      rows = statement.getUpdateCount();
      // 把selectKey方式获取的主键设置到参数对象中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      // 执行sql
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleResultSets(statement);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleCursorResultSets(statement);
  }

  /**
   * 实例化语句
   * @param connection 数据库连接
   * @return
   * @throws SQLException
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // 如果结果集类型是默认类型直接创建Statement
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.createStatement();
    } else {
      // 否则创建支持并发读的Statement 该Statement不支持update操作
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  @Override
  public void parameterize(Statement statement) {
    // N/A
  }

}
