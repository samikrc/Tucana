package com.ilabs.dsi.tucana

import akka.actor.{ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.http.scaladsl.Http
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import com.ilabs.dsi.tucana.utils.ConfigManager
import org.slf4j.LoggerFactory

object PredictionServer extends App with PredictionServerRoutes
{
    // Set up logger
    val log = LoggerFactory.getLogger(this.getClass)

    if(args.length == 0)
    {
        println("Usage: PredictServer [local OR server] [/path/to/config/file]")
        println("Defaults:")
        println("  1st arg: local")
        println("  2nd arg: local-config.conf")
    }

    // The following variables will be accessible from other classes
    val execType = if(args.length > 0) args(0) else "local"
    val configFile = if(args.length > 1) args(1) else s"$execType-config.conf"
    log.info(s"Using execType: $execType, config file: $configFile ...")

    override implicit val system = ActorSystem("PredictionServer")
    override implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executor = system.dispatcher
    // LRU Cache Actor
    val cacheActor = system.actorOf(Props[LRUCacheActor], name = "LRUCacheActor")

    //Spark Actor props block
    val sparkActorStrategy = OneForOneStrategy()
    {
        case _ => SupervisorStrategy.restart
    }
    val sparkProps = Props(new SparkPredictionActor(cacheActor))
    val sparkRouterProps = RoundRobinPool(
        ConfigManager.get("sparkActor.pool.concurrency").toInt,
        supervisorStrategy = sparkActorStrategy).props(routeeProps = sparkProps)
    val sparkRouter = system.actorOf(sparkRouterProps, name = "SparkPredictorActor")

    val bindingFuture = Http().bindAndHandle(route, ConfigManager.get("http.interface"), ConfigManager.get("http.port").toInt)
    log.info(s"Metadata Server online at http://${ConfigManager.get("http.interface")}:${ConfigManager.get("http.port")}/")
}