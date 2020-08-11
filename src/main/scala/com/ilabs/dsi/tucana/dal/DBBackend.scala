package com.ilabs.dsi.tucana.dal

import java.sql.{Connection, PreparedStatement, ResultSet}

import resource.managed

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait DBBackend
{
    def getConnection: Connection

    /**
      * Method to execute a query and return a single value as result.
      * @param query
      * @return
      */
    def queryWithSingleResult[T](query: String, params: Array[Any]): Option[T] =
    {
        val mresultset = for
            (
            conn <- managed(getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => setParam(myst, i + 1, value) }
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
    def queryWithResult(query: String, params: Array[Any]): Seq[mutable.LinkedHashMap[String, Any]] =
    {
        def getTypedValue(rs: ResultSet, index: Int) =
        {
            rs.getMetaData.getColumnClassName(index) match
            {
                case "java.lang.String" => rs.getString(index)
                case "java.lang.Integer" => rs.getInt(index)
                case "java.sql.Blob" => rs.getBytes(index)
                case "java.sql.Clob" => rs.getString(index)
                case _ => ???
            }
        }

        val mresultset = for
            (
            conn <- managed(getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => setParam(myst, i + 1, value) }
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
                    // In the below line, the column name is always returned in upper case.
                    for (i <- 1 to colCount) row += (rs.getMetaData.getColumnName(i) -> getTypedValue(rs, i))
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
    def queryWithNoResult(query: String, params: Array[Any]) =
    {
        for
            (
            conn <- managed(getConnection);
            pst <- managed(
                {
                    val myst = conn.prepareStatement(query)
                    params.zipWithIndex.foreach{ case (value, i) => setParam(myst, i + 1, value) }
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
    private def setParam(st: PreparedStatement, index: Int, param: Any) =
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
