package com.ilabs.dsi.tucana.dal

import java.sql.{Connection, PreparedStatement}

import com.ilabs.dsi.tucana.utils.ConfigManager
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.slf4j.LoggerFactory
import resource._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object MySQLBackend
{
    private val config: HikariConfig = new HikariConfig()
    config.setJdbcUrl(ConfigManager.get("mysql.url"))
    config.setUsername(ConfigManager.get("mysql.user"))
    config.setPassword(ConfigManager.get("mysql.password"))
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    //throw new UnsupportedOperationException("Test Exception")
    private val ds = new HikariDataSource(config)

    /**
      * Method to get a connection from the connection pool
      * @return
      */
    def getConnection: Connection = ds.getConnection
}

class MySQLBackend extends DBBackend
{
    // Set up logger
    private val log = LoggerFactory.getLogger(this.getClass)

    /**
      * Method to execute a query and return a single value as result
      * @param query
      * @return
      */
    override def queryWithSingleResult[T](query: String, params: Array[Any]): Option[T] =
    {
        val mresultset = for
            (
            conn <- managed(MySQLBackend.getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => addParam(myst, i + 1, value) }
                    myst
                });
            rs <- managed(pst.executeQuery())
        ) yield rs

        val result = mresultset.acquireAndGet
        {
            rs => if(rs.next()) Some(rs.getObject(1).asInstanceOf[T]) else None
        }
        result
    }

    /**
      * Method to retrieve a collection of rows for a query.
      * @param query
      * @param params
      * @return
      */
    override def queryWithResult(query: String, params: Array[Any]): Seq[mutable.LinkedHashMap[String, Any]] =
    {
        val mresultset = for
            (
            conn <- managed(MySQLBackend.getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => addParam(myst, i + 1, value) }
                    myst
                });
            rs <- managed(pst.executeQuery())
        ) yield rs

        val result = mresultset.acquireAndGet
        {
            rs =>
            {
                val resultAccum = ArrayBuffer[mutable.LinkedHashMap[String, Any]]()
                val colCount = rs.getMetaData.getColumnCount
                while(rs.next())
                {
                    val row = mutable.LinkedHashMap[String, Any]()
                    for (i <- 1 to colCount) row += (rs.getMetaData.getColumnName(i) -> rs.getObject(i))
                    resultAccum += row
                }
                resultAccum
            }
        }
        result
    }

    /**
      * Method to run query with no result, e.g., insert queries.
      * @param query
      * @param params
      */
    override def queryWithNoResult(query: String, params: Array[Any]) =
    {
        for
            (
            conn <- managed(MySQLBackend.getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => addParam(myst, i + 1, value) }
                    myst
                })
        )
        {
            pst.executeUpdate()
        }
    }

    /**
      * Internal method to add parameters to a PreparedStatement
      * @param st
      * @param index
      * @param param
      */
    private def addParam(st: PreparedStatement, index: Int, param: Any) =
    {
        param match
        {
            case v: String => st.setString(index, v)
            case v: Int => st.setInt(index, v)
            case v: Float => st.setFloat(index, v)
            case v: Double => st.setDouble(index, v)
            case v: Boolean => st.setBoolean(index, v)
            case v: Array[Byte] => st.setBytes(index, v)
            case _ => throw new NotImplementedError(s"Parameter type for value $param is not supported!")
        }
    }
}
