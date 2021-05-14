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
package org.apache.ibatis.executor;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 批执行器
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  private final List<Statement> statementList = new ArrayList<>();
  private final List<BatchResult> batchResultList = new ArrayList<>();
  private String currentSql;
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   * 操作数据库更新
   * @param ms 映射语句
   * @param parameterObject 参数
   * @return
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    // 创建语句处理器
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    // 获取绑定的sql
    final BoundSql boundSql = handler.getBoundSql();
    final String sql = boundSql.getSql();
    final Statement stmt;
    // 如果当前
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      int last = statementList.size() - 1;
      stmt = statementList.get(last);
      // 设置事务超时时间
      applyTransactionTimeout(stmt);
      // 为语句设置参数
      handler.parameterize(stmt);// fix Issues 322
      // 获取批处理结果
      BatchResult batchResult = batchResultList.get(last);
      // 追加参数值对象
      batchResult.addParameterObject(parameterObject);
    } else {
      Connection connection = getConnection(ms.getStatementLog());
      // 创建statement
      stmt = handler.prepare(connection, transaction.getTimeout());
      // 为语句设置参数
      handler.parameterize(stmt);    // fix Issues 322
      // 记录当前执行器对象最后一次执行的SQL
      currentSql = sql;
      // 记录当前执行器对象最后一次执行的statement
      currentStatement = ms;
      // 记录当前执行器创建的所有statement
      statementList.add(stmt);
      // 记录所有statement的批处理结果
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    // 追加参数集合
    handler.batch(stmt);
    return BATCH_UPDATE_RETURN_VALUE;
  }

  /**
   * 操作数据库查询
   *
   * @param ms 映射语句
   * @param parameterObject 参数对象
   * @param rowBounds 翻页限制条件
   * @param resultHandler 结果处理器
   * @param boundSql 查询语句
   * @param <E> 结果类型
   * @return
   * @throws SQLException
   */
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      // 刷新语句
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      // 新建语句处理器
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      // 新建语句处理器
      stmt = handler.prepare(connection, transaction.getTimeout());
      // 执行sql语句 并传入结果处理器 处理好结果返回
      handler.parameterize(stmt);
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  /**
   * 刷新批处理语句
   * @param isRollback
   * @return
   * @throws SQLException
   */
  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      List<BatchResult> results = new ArrayList<>();
      if (isRollback) {
        return Collections.emptyList();
      }
      // 遍历每个statement
      for (int i = 0, n = statementList.size(); i < n; i++) {
        Statement stmt = statementList.get(i);
        // 设置超时时间
        applyTransactionTimeout(stmt);
        // 获取每个statement的批处理结果
        BatchResult batchResult = batchResultList.get(i);
        try {
          // 设置批处理更新数量
          batchResult.setUpdateCounts(stmt.executeBatch());
          // 获取映射语句
          MappedStatement ms = batchResult.getMappedStatement();
          // 获取批处理结果的参数对象
          List<Object> parameterObjects = batchResult.getParameterObjects();
          // 获取主键生成器
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          // 如果是数据库自增主键生成器
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            // 把主键设置到参数值对象中
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141 如果不是不生成主键
            for (Object parameter : parameterObjects) {
              // 执行完sql之后再获取主键
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          // Close statement to close cursor #1109
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
              .append(" (batch index #")
              .append(i + 1)
              .append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ")
                .append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
