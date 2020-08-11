package com.ilabs.dsi.tucana.dal

import java.sql.DriverManager

import com.ilabs.dsi.tucana.utils.ConfigManager

/**
  * H2 Backend for testing purposes.
  */
class H2Backend extends DBBackend
{
    Class.forName(ConfigManager.get("h2.driver"))

    /**
      * Method to get a connection object. Will be automatically closed with ARM (Automatic Resource Management).
      * @return
      */
    override def getConnection =  DriverManager.getConnection(ConfigManager.get("h2.url"), ConfigManager.get("h2.user"), ConfigManager.get("h2.password"))
}
