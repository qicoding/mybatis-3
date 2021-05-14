/**
 *    Copyright 2009-2016 the original author or authors.
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * 语句处理器接口
 * @author Clinton Begin
 */
public interface StatementHandler {

  /**
   * 获取Statement
   * @param connection 数据库连接
   * @param transactionTimeout 事务超时时间
   * @return
   * @throws SQLException
   */
  Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;

  /**
   * 为语句设置参数
   * @param statement 语句
   * @throws SQLException
   */
  void parameterize(Statement statement)
      throws SQLException;

  /**
   * 批处理操作追加参数集合
   * @param statement
   * @throws SQLException
   */
  void batch(Statement statement)
      throws SQLException;

  /**
   * 执行更新（含插入、修改、删除）
   * @param statement
   * @return 返回更新的数量
   * @throws SQLException
   */
  int update(Statement statement)
      throws SQLException;

  /**
   * 执行查询
   * @param statement 语句
   * @param resultHandler 结果处理器
   * @param <E> 泛型结果
   * @return 返回查询结果
   * @throws SQLException
   */
  <E> List<E> query(Statement statement, ResultHandler resultHandler)
      throws SQLException;

  /**
   * 查询游标
   * @param statement 语句
   * @param <E>
   * @return
   * @throws SQLException
   */
  <E> Cursor<E> queryCursor(Statement statement)
      throws SQLException;

  /**
   * 获取绑定SQL
   * @return
   */
  BoundSql getBoundSql();

  /**
   * 获取参数处理器
   * @return
   */
  ParameterHandler getParameterHandler();

}
