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
package org.apache.ibatis.session;

/**
 * 指定MyBatis是否以及如何自动将列映射到fieldsproperties。
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 *
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior {

  /**
   * 禁用自动映射
   * Disables auto-mapping.
   */
  NONE,

  /**
   * 仅自动映射结果，内部没有定义嵌套的结果映射。
   * Will only auto-map results with no nested result mappings defined inside.
   */
  PARTIAL,

  /**
   * 将自动映射任何复杂性（包括嵌套或其他形式）的结果映射
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   */
  FULL
}
