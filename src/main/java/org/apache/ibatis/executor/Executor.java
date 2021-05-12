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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 执行器接口
 * @author Clinton Begin
 */
public interface Executor {

  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 数据更新操作，其中数据的增加、删除、更新均可由该方法实现
   * @param ms
   * @param parameter
   * @return
   * @throws SQLException
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 查询数据库中的数据
   * @param ms 映射语句
   * @param parameter 参数对象
   * @param rowBounds 翻页限制条件
   * @param resultHandler 结果处理器
   * @param cacheKey 缓存的键
   * @param boundSql 查询语句
   * @param <E> 结果类型
   * @return 结果列表
   * @throws SQLException
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  /**
   * 数据查询操作，返回结果为列表形式
   * @param ms 映射语句对象
   * @param parameter 参数对象
   * @param rowBounds 翻页限制
   * @param resultHandler 结果处理器
   * @param <E> 输出结果类型
   * @return 查询结果
   * @throws SQLException
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  /**
   * 数据查询操作，返回结果为游标形式
   * @param ms 映射语句对象
   * @param parameter 参数对象
   * @param rowBounds 翻页限制
   * @param <E> 输出结果类型
   * @return
   * @throws SQLException
   */
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  /**
   * 清理缓存
   * @return
   * @throws SQLException
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 提交事务
   * @param required
   * @throws SQLException
   */
  void commit(boolean required) throws SQLException;

  /**
   * 回滚事务
   * @param required
   * @throws SQLException
   */
  void rollback(boolean required) throws SQLException;

  /**
   * 创建当前查询的缓存键值
   * @param ms 映射语句对象
   * @param parameterObject 参数对象
   * @param rowBounds 翻页限制
   * @param boundSql 绑定SQL
   * @return
   */
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 本地缓存是否有指定值
   * @param ms 映射语句对象
   * @param key 本地缓存key
   * @return
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  /**
   * 清理本地缓存
   */
  void clearLocalCache();

  /**
   * 懒加载
   * @param ms 映射语句对象
   * @param resultObject 参数对象
   * @param property 属性
   * @param key 本地缓存key
   * @param targetType 目标类型
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取事务
   * @return
   */
  Transaction getTransaction();

  /**
   * 关闭执行器
   * @param forceRollback
   */
  void close(boolean forceRollback);

  /**
   *  判断执行器是否关闭
   * @return
   */
  boolean isClosed();

  /**
   * 设置执行器包装
   * @param executor
   */
  void setExecutorWrapper(Executor executor);

}
