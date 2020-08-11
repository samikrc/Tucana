package com.ilabs.dsi.tucana.dal

import scala.collection.mutable

trait DBBackend
{
    def queryWithSingleResult[T](query: String, params: Array[Any]): Option[T]
    def queryWithResult(query: String, params: Array[Any]): Seq[mutable.LinkedHashMap[String, Any]]
    def queryWithNoResult(query: String, params: Array[Any])
}
