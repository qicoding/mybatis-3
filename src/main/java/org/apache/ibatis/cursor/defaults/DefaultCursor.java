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
package org.apache.ibatis.cursor.defaults;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * This is the default implementation of a MyBatis Cursor.
 * This implementation is not thread safe.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class DefaultCursor<T> implements Cursor<T> {

  /** 默认结果集处理类 */
  // ResultSetHandler stuff
  private final DefaultResultSetHandler resultSetHandler;
  /** 结果map映射关系 */
  private final ResultMap resultMap;
  /** 结果集包装类 */
  private final ResultSetWrapper rsw;
  /** 翻页限制 物理分页 */
  private final RowBounds rowBounds;
  /** 对象包装结果处理类 */
  protected final ObjectWrapperResultHandler<T> objectWrapperResultHandler = new ObjectWrapperResultHandler<>();

  /** 游标迭代器 */
  private final CursorIterator cursorIterator = new CursorIterator();
  /** 确保不能多次打开游标，只能打开一次 */
  private boolean iteratorRetrieved;

  /** 游标的默认状态是创建 */
  private CursorStatus status = CursorStatus.CREATED;
  /** 分页对象的索引位置为-1 */
  private int indexWithRowBound = -1;

  /**
   * 游标的状态
   */
  private enum CursorStatus {

    /**
     * 新创建的游标，数据库ResultSet还未被消费
     * A freshly created cursor, database ResultSet consuming has not started.
     */
    CREATED,
    /**
     * 当前正在使用的游标已开始消费数据库ResultSet
     * A cursor currently in use, database ResultSet consuming has started.
     */
    OPEN,
    /**
     * 游标已被关闭，未完全消费掉
     * A closed cursor, not fully consumed.
     */
    CLOSED,
    /**
     * 一个完全消费掉的游标，一个消费完的游标总是关闭的。
     * A fully consumed cursor, a consumed cursor is always closed.
     */
    CONSUMED
  }

  /**
   * 构造器 创建一个默认的游标
   * @param resultSetHandler 默认结果集处理类
   * @param resultMap 结果映射map
   * @param rsw 结果集包装类
   * @param rowBounds 分页对象
   */
  public DefaultCursor(DefaultResultSetHandler resultSetHandler, ResultMap resultMap, ResultSetWrapper rsw, RowBounds rowBounds) {
    this.resultSetHandler = resultSetHandler;
    this.resultMap = resultMap;
    this.rsw = rsw;
    this.rowBounds = rowBounds;
  }

  /**
   * 判断游标是否被打开
   * @return
   */
  @Override
  public boolean isOpen() {
    return status == CursorStatus.OPEN;
  }

  /**
   * 判断游标是否被消费掉
   * @return
   */
  @Override
  public boolean isConsumed() {
    return status == CursorStatus.CONSUMED;
  }

  /**
   * 获取当前的索引
   * 分页偏移量 + 游标迭代器索引位置
   * @return
   */
  @Override
  public int getCurrentIndex() {
    return rowBounds.getOffset() + cursorIterator.iteratorIndex;
  }

  /**
   * 获取迭代器
   * @return
   */
  @Override
  public Iterator<T> iterator() {
    if (iteratorRetrieved) {
      throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
    }
    if (isClosed()) {
      throw new IllegalStateException("A Cursor is already closed.");
    }
    iteratorRetrieved = true;
    return cursorIterator;
  }

  /**
   * 关闭游标
   */
  @Override
  public void close() {
    if (isClosed()) {
      return;
    }

    ResultSet rs = rsw.getResultSet();
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    } finally {
      status = CursorStatus.CLOSED;
    }
  }

  protected T fetchNextUsingRowBound() {
    T result = fetchNextObjectFromDatabase();
    // 过滤到偏移量的位置
    while (objectWrapperResultHandler.fetched && indexWithRowBound < rowBounds.getOffset()) {
      result = fetchNextObjectFromDatabase();
    }
    return result;
  }

  /**
   * 从数据库中获取下一个对象
   * @return
   */
  protected T fetchNextObjectFromDatabase() {
    // 判断游标是否已经关闭，已关闭返回null
    if (isClosed()) {
      return null;
    }
    try {
      objectWrapperResultHandler.fetched = false;
      // 设置当前状态是游标打开状态
      status = CursorStatus.OPEN;
      // 如果结果集包装类不是已经关闭
      // 把结果放入objectWrapperResultHandler对象的result中
      if (!rsw.getResultSet().isClosed()) {
        resultSetHandler.handleRowValues(rsw, resultMap, objectWrapperResultHandler, RowBounds.DEFAULT, null);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // 获取对象包装处理的结果
    // 如果结果不为空结果， 索引++
    T next = objectWrapperResultHandler.result;
    if (objectWrapperResultHandler.fetched) {
      indexWithRowBound++;
    }
    // No more object or limit reached
    // next为null或者读取条数等于偏移量+限制条数
    if (!objectWrapperResultHandler.fetched || getReadItemsCount() == rowBounds.getOffset() + rowBounds.getLimit()) {
      close();
      status = CursorStatus.CONSUMED;
    }
    // 把结果设置为null
    objectWrapperResultHandler.result = null;

    return next;
  }

  /**
   * 判断是否关闭
   * 游标本身处于关闭状态，或者已经取出结果的所有元素
   * @return
   */
  private boolean isClosed() {
    return status == CursorStatus.CLOSED || status == CursorStatus.CONSUMED;
  }

  /**
   * 下一个读取索引位置
   * @return
   */
  private int getReadItemsCount() {
    return indexWithRowBound + 1;
  }

  /**
   * 对象结果集包装类
   * @param <T>
   */
  protected static class ObjectWrapperResultHandler<T> implements ResultHandler<T> {

    /** 结果集 */
    protected T result;
    protected boolean fetched;

    /**
     * 下文中获取结果集
     * @param context
     */
    @Override
    public void handleResult(ResultContext<? extends T> context) {
      this.result = context.getResultObject();
      context.stop();
      fetched = true;
    }
  }

  /**
   * 游标迭代器对象
   */
  protected class CursorIterator implements Iterator<T> {

    /**
     * 保存下一个将会被返回的对象
     * Holder for the next object to be returned.
     */
    T object;

    /**
     * 返回下一个对象的索引
     * Index of objects returned using next(), and as such, visible to users.
     */
    int iteratorIndex = -1;

    /**
     * 是否有下个
     * @return
     */
    @Override
    public boolean hasNext() {
      if (!objectWrapperResultHandler.fetched) {
        object = fetchNextUsingRowBound();
      }
      return objectWrapperResultHandler.fetched;
    }

    @Override
    public T next() {
      // Fill next with object fetched from hasNext()
      // 接下来填充从hasNext()获取的对象
      T next = object;

      // 未执行过hasNext
      if (!objectWrapperResultHandler.fetched) {
        next = fetchNextUsingRowBound();
      }
      // 执行过 hasNext
      if (objectWrapperResultHandler.fetched) {
        objectWrapperResultHandler.fetched = false;
        object = null;
        iteratorIndex++;
        return next;
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove element from Cursor");
    }
  }
}
