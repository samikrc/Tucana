package com.ilabs.dsi.tucana.functionalTests

import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager}
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestActorRef
import akka.util.ByteString
import com.ilabs.dsi.tucana.dal.H2Backend
import com.ilabs.dsi.tucana.utils.ConfigManager
import com.ilabs.dsi.tucana.{LRUCacheActor, PredictionServerRoutes, SparkPredictionActor}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.io.File

class PredictionAPITests extends WordSpec with ScalatestRouteTest with Matchers with ScalaFutures with PredictionServerRoutes
{
    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(100 seconds)

    // This is to initialize the database with the preloaded models, so that all our predict requests will use some model by default.
    def initializeDB(): Unit =
    {
        val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        val dateStorageformat = new SimpleDateFormat(DATETIME_FORMAT)

        val db = new H2Backend

        // Set up the initial tables
        val statements = Array(
            "drop table if exists mdr_users",
            "drop table if exists models",
            "create table if not exists mdr_users (userId varchar(200) not null primary key, devKey varchar(200) not null unique, registerDate DATETIME not null, isAdmin boolean)",
            "create table if not exists models (modelId varchar(200) not null, version varchar(100) not null, devKey varchar(200) not null references mdr_users(devKey), description varchar(250), lastUpdateTimestamp text, model longblob, modelLocation varchar(1000), fvSchema text, primary key (modelId, version))",
            "delete from mdr_users where userId='tucanaUser'",
            s"insert into mdr_users values('tucanaUser', 'a998274b98', '${dateStorageformat.format(Date.from(Instant.now))}', true)",
            "delete from models where modelId='test_model' and version = 'v1'",
        )
        statements.foreach(db.queryWithNoResult(_, Array()))
        // Set up the model file
        val modelFilePath = Paths.get(getClass.getResource("/flashml-noPage.zip").toURI)
        val modelBytes = Files.readAllBytes(modelFilePath)
        db.queryWithNoResult("insert into models(modelId, version, devKey, lastUpdateTimestamp, model, fvSchema) values(?, ?, ?, ?, ?, ?)",
            Array("test_model", "v1", "a998274b98", dateStorageformat.format(Date.from(Instant.now)), modelBytes, """{"fields":[{"type":"string","name":"lineText"}],"topKCol":"top_intents"}""")
        )
        println("H2 DB initialized.")

    }

    initializeDB()
    val cacheRef = TestActorRef[LRUCacheActor](new LRUCacheActor())
    override val sparkRouter = TestActorRef[SparkPredictionActor](new SparkPredictionActor(cacheRef))
    val webInput = ByteString("""{"row":["_class_hello i want to cancel my _class_personal broadband connection"]}""".stripMargin)

    // Test case for intent prediction request
    "Path /predictserver/v1/models/test_model/versions/v1/predict" should
    {

        val predictRequest = HttpRequest(
            HttpMethods.GET,
            uri = "/predictserver/v1/models/test_model/versions/v1/predict",
            entity = HttpEntity(MediaTypes.`application/json`, webInput))

        "return the response with following probability " in
        {
            // tests:
            predictRequest ~> RawHeader("tucana-devKey", "a998274b98") ~> route ~> check
            {
                responseAs[String] shouldEqual """{"prediction_label":"my_account","probability":"0.14276698715633568"}"""
            }
        }

        "return user not found for the given key" in
        {
            // tests:
            predictRequest ~> RawHeader("tucana-devKey", "a998274b99") ~> route ~> check
            {
                responseAs[String].stripLineEnd shouldEqual """{"error":{"internalCode":1,"type":"SQLException","message":"User not found for the given devkey. Devkey may not be registered one."}}"""
            }
        }

        "return tucana devkey is not valid" in
        {
            predictRequest ~> RawHeader("tucana-devKey", "a998274b-9") ~> route ~> check
            {
                responseAs[String].stripLineEnd shouldEqual """{"error":{"internalCode":3,"type":"Invalid","message":"DevKey is not valid. It should contain only alphanumeric values."}}"""
            }
        }
    }

    //Test case for topk intent prediction request
    "Path /predictserver/v1/models/test_model/versions/v1/predict-topk" should
    {

        val predictTopKRequest = HttpRequest(
            HttpMethods.GET,
            uri = "/predictserver/v1/models/test_model/versions/v1/predict-topk",
            entity = HttpEntity(MediaTypes.`application/json`, webInput))

        "return the response with following top n intents and probability" in
        {
            predictTopKRequest ~> RawHeader("tucana-devKey", "a998274b98") ~> route ~> check
            {
                responseAs[String] shouldEqual ("{\"intents\":[{\"className\":\"my_account\",\"score\":0.14276698715633568},{\"className\":\"billing_or_credit_enquiry_or_action\"," +
                        "\"score\":0.13222848400914222},{\"className\":\"contract_enquiry\",\"score\":0.11781919343768565},{\"className\":\"sales_support_order_or_delivery_enquiry_or_action\",\"score\":0.07941565227027043},{\"className\":\"pcs_order_or_delivery_enquiry_or_action\",\"score\":0.07591690097058783},{\"className\":\"data_query\",\"score\":0.0749987330578579},{\"className\":\"sales_mobile\",\"score\":0.06307488460836588},{\"className\":\"payment_or_action\",\"score\":0.057214574259720964},{\"className\":\"network_issue_or_faults\",\"score\":0.051763510342926276},{\"className\":\"mobile_insurance_or_warranty\",\"score\":0.042019859032301515}]}")
            }
        }

        "return tucana devkey is not found in the header" in
        {
            predictTopKRequest ~> route ~> check
            {
                responseAs[String].stripLineEnd shouldEqual """{"error":{"internalCode":2,"type":"NotAuthorized","message":"Unauthorized due to missing tucana-devKey"}}"""
            }
        }
    }
}
