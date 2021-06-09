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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * 缓存key包装类
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  /**
   * 空缓存key
   */
  public static final CacheKey NULL_CACHE_KEY = new CacheKey() {

    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }

    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  /** 计算hashcode的默认乘数因子 */
  private static final int DEFAULT_MULTIPLIER = 37;
  /** 默认hashcode值 */
  private static final int DEFAULT_HASHCODE = 17;

  /** 计算hashcode的乘数因子 */
  private final int multiplier;
  /** hashcode值 */
  private int hashcode;
  /** 用于校验的和 */
  private long checksum;
  /** cacheKey包装对象计算hash的对象个数  */
  private int count;
  /** cacheKey包装对象计算hash的对象列表  */
  // 8/21/2017 - Sonarlint flags this as needing to be marked transient. While true if content is not serializable, this
  // is not always true and thus should not be marked transient.
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  /**
   * 更新CacheKey 主要是重算hashcode
   * @param object
   */
  public void update(Object object) {
    // 通过 {@link ArrayUtil} 获取到对象的hashCode
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    count++;
    checksum += baseHashCode;
    baseHashCode *= count;

    hashcode = multiplier * hashcode + baseHashCode;

    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  /**
   * 重写equals方法
   * @param object
   * @return
   */
  @Override
  public boolean equals(Object object) {
    // 判断地址
    if (this == object) {
      return true;
    }
    // 非CacheKey
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;
    // hashcode 不相同直接return false
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    // 校验和不相等直接return false
    if (checksum != cacheKey.checksum) {
      return false;
    }
    // 计数数量不相等直接return false
    if (count != cacheKey.count) {
      return false;
    }

    // 遍历对象列表 判断每个对象是否相等
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      // 通过 {@link ArrayUtil} 校验相等
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  /**
   * 重写toString方法
   * @return
   */
  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  /**
   * 重写clone方法
   * @return
   * @throws CloneNotSupportedException
   */
  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
