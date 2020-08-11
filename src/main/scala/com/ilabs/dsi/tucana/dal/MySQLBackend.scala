package com.ilabs.dsi.tucana.dal

import java.sql.Connection

import com.ilabs.dsi.tucana.utils.ConfigManager
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

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

/**
  * MySQL backend for executing queries
  */
class MySQLBackend extends DBBackend
{
    override def getConnection = MySQLBackend.getConnection
}
