package com.ilabs.dsi.tucana

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes.{Forbidden, InternalServerError, NotFound}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, handleExceptions, headerValueByName, pathPrefix, _}
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.ilabs.dsi.tucana.dal.QueryManager
import com.ilabs.dsi.tucana.utils.{ConfigManager, ErrorConstants, Json}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

trait PredictionServerRoutes
{
    // Set up logger
    private val log = LoggerFactory.getLogger(this.getClass)

    implicit val system: ActorSystem
    implicit val materializer: ActorMaterializer

    implicit def executor: ExecutionContextExecutor

    // Define an exception handler for the possible exceptions that arise.
    val serverExceptionHandler = ExceptionHandler
    {
        case ex: Exception => complete(HttpResponse(StatusCodes.InternalServerError, entity = s"${ex.getMessage}\n"))
    }

    val serverRejectionHandler: RejectionHandler =
        RejectionHandler.newBuilder()
                .handle{ case ValidationRejection(msg, _) => completeWithError(msg) }
                .handle
                { case MissingHeaderRejection(str) =>
                    if (str == "tucana-devKey")
                        completeWithError(ErrorConstants.MISSING_KEY)
                    else
                        completeWithError(s"Missing Header: $str")
                }
                .result()

    val sparkRouter: ActorRef

    val route: Route = (handleExceptions(serverExceptionHandler)
            & handleRejections(serverRejectionHandler)
            & pathPrefix("predictserver" / "v1")
            & decodeRequest
            & cors()
            & withSizeLimit(ConfigManager.get("request.bytes.size").toLong)
            & headerValueByName("tucana-devKey"))
    {
        devKey =>
        {
            (validate(checkIfValidDevkey(devKey), ErrorConstants.INVALID_DEVKEY) & validate(QueryManager.checkIfUserRegistered(devKey), ErrorConstants.USER_NOT_FOUND))
            {
                (pathPrefix("models" / Segment / "versions" / Segment / """(predict-topk|predict)""".r) & get & entity(as[String]))
                {
                    (modelId, version, predictOp, webInput) =>
                    {
                        implicit val timeout = Timeout(10 second)
                        val resultJson = Await.result(sparkRouter ? (modelId, version, webInput, predictOp), timeout.duration).asInstanceOf[String]
                        validate(!resultJson.equals(""), ErrorConstants.MODEL_LOAD_ERROR)
                        {
                            complete(HttpEntity(ContentTypes.`application/json`, resultJson))
                        }
                    }
                }
            }
        }
    }

    private def checkIfValidDevkey(devKey: String): Boolean =
    {
        devKey.matches("^[a-zA-Z0-9]*$")
    }

    /**
      * Method to complete a request with error message.
      * @param error
      * @return
      */
    private def completeWithError(error: String): StandardRoute =
    {
        def errorDecoder(error: String) =
        {
            error match
            {
                case ErrorConstants.USER_NOT_FOUND => ErrorInfo(1, NotFound, "SQLException")
                case ErrorConstants.MISSING_KEY => ErrorInfo(2, Forbidden, "NotAuthorized")
                case ErrorConstants.INVALID_DEVKEY => ErrorInfo(3, Forbidden, "Invalid")
                case ErrorConstants.SERVICE_DOWN => ErrorInfo(500, InternalServerError, "InternalServerError")
                case _ => ErrorInfo(10, InternalServerError, "InternalServerError")
            }
        }
        val err = errorDecoder(error)
        complete(err.status, Json.Value(Map("error" -> Map("internalCode" -> err.internalCode, "type" -> err.`type`, "message" -> error))).writeln)
    }

    case class ErrorInfo(internalCode: Int, status: StatusCode, `type`: String)
}