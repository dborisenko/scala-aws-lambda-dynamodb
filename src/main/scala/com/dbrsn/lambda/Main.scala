package com.dbrsn.lambda

import java.io.{InputStream, OutputStream}
import java.sql.Timestamp
import java.time.{Clock, LocalDateTime}
import java.util.UUID

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.amazonaws.services.lambda.runtime.Context
import com.dbrsn.lambda.Order.ClientId
import com.dbrsn.lambda.PersistedOrder.OrderId
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.parser._
import io.circe.syntax._
import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Input order
  */
final case class Order(clientId: ClientId, numbers: Set[Int])

object Order {
  final type ClientId = String
}

/**
  * Output and persisted order
  */
final case class PersistedOrder(orderId: OrderId, at: LocalDateTime, order: Order)

object PersistedOrder {
  final type OrderId = String
}

class Main {
  // Initializing DynamoDB client
  lazy val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_1).build()
  lazy val dynamoDb: DynamoDB = new DynamoDB(client)

  lazy val clock: Clock = Clock.systemUTC()

  val tableName: String = "numbers-db"
  val encoding = "UTF-8"

  object field {
    val orderId: String = "id"
    val at: String = "at"
    val clientId: String = "clientId"
    val numbers: String = "numbers"
  }

  def post(input: InputStream, output: OutputStream, context: Context): Unit = {
    // Parsing order from input stream
    val order = parse(IOUtils.toString(input, encoding)).flatMap(_.as[Order])
    // Wrapping it to an object, which we would like to persist
    val persistedOrder = order.map(PersistedOrder(UUID.randomUUID().toString, LocalDateTime.now(clock), _))

    val persisted = persistedOrder.flatMap { o =>
      // Getting DynamoDB table
      val table = dynamoDb.getTable(tableName)
      Try {
        // Creating new DynamoDB item
        val item = new Item()
          .withPrimaryKey(field.orderId, o.orderId)
          .withLong(field.at, Timestamp.valueOf(o.at).getTime)
          .withString(field.clientId, o.order.clientId)
          .withList(field.numbers, o.order.numbers.toList.asJava)
        // Persisting item to DynamoDB
        table.putItem(item)
        o
      }.toEither
    }

    // Throw exception if it happened or write output order in json format otherwise
    persisted.map(_.asJson).fold(throw _, json => {
      IOUtils.write(json.noSpaces, output, encoding)
      output.flush()
    })
  }

}
