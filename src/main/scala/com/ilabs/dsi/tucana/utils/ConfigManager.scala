package com.ilabs.dsi.tucana.utils

import java.io.{File, IOException}

import com.ilabs.dsi.tucana.PredictionServer
import com.typesafe.config.{Config, ConfigFactory}

/**
 * @since 4/6/18
 */
object ConfigManager
{
    private val config: Config = readConfigFile

    /**
      * Method to read a config file
      * @return
      */
    private def readConfigFile: Config =
    {
        // Get the default configs loaded first
        val configs = ConfigFactory.load()
        if(configs.getString("fixture") == "test")
        {
            // All set - no other config file to read
            configs
        }
        else
        {
            val configFile = PredictionServer.configFile
            val fileObj = new File(configFile)
            if (fileObj.exists())
                ConfigFactory.parseFile(fileObj)
            else
                throw new IOException(s"Config file doesn't exist: $configFile")
        }
    }

    /**
      * Method to get a config from the config file.
      * @param key
      * @return
      */
    def get(key: String): String = config.getAnyRef(key).toString
}
