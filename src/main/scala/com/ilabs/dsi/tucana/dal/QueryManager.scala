package com.ilabs.dsi.tucana.dal

import java.sql.{Blob, Clob}

import com.ilabs.dsi.tucana.utils.ConfigManager
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * Class with collection of queries to coordinate API calls with the DB backend.
  * Currently supports mysql and H2 backend.
  */
object QueryManager
{
    // Set up logger
    private val log = LoggerFactory.getLogger(this.getClass)

    // Get the database type
    private val dbType = ConfigManager.get("db")

    // Get the DB reference
    private val db = dbType match
    {
        case "mysql" =>  new MySQLBackend
        case "h2" => new H2Backend
    }

    /**
      * Check if a user with a particular devKey is registered.
      * @param devKey
      * @return
      */
    def checkIfUserRegistered(devKey: String): Boolean =
    {
        val response = db.queryWithSingleResult[String]("select userId from mdr_users where devKey=?", Array(devKey))
        response match
        {
            case Some(_) => true
            case None => false
        }
    }

    def checkIfViewExist(viewId: String, version: String): Boolean =
    {
        ???
    }

    def getView(viewId: String, version: String): mutable.Map[String, Any] =
    {
        ???
    }

    def checkIfModelExist(modelId: String, version: String): Boolean =
    {
        val response = db.queryWithSingleResult[String]("select lastUpdateTimestamp from models where modelId=? and version=?", Array(modelId, version))
        response  match
        {
            case Some(_) => true
            case None => false
        }
    }

    def getModel(modelId: String, version: String) =
    {
        val dbResponse = db.queryWithResult("select model, ifnull(modelLocation, '') as modelLocation, fvSchema from models where modelId=? and version=?", Array(modelId, version)).head
        // We are guaranteed that the model exists (since we would have done exist check before calling this method)
        val (model, modelLocation, fvSchema) =
        (
            dbResponse("model".toUpperCase).asInstanceOf[Array[Byte]],
            dbResponse("modelLocation".toUpperCase).asInstanceOf[String],
            dbResponse("fvSchema".toUpperCase).asInstanceOf[String]
        )
        // Check if this model has to be picked up from a file location
        if(modelLocation == "")
            (model, fvSchema)
        else
        {
            // We need to read the model from a filesystem
            ???
        }
    }
}
