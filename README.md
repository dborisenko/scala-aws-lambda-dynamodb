# Example from my [blog](http://dbrsn.com/2017-06-18-how-to-build-scala-tiny-backend-on-amazon-aws-lambda/)

We are going to create a simple application, which posts and gets provided to lambda set of numbers.

# Initialize AWS services

## Create S3 bucket

First of all, let's go to amazon aws console and create a bucket with name `scala-aws-lambda`. We need it for storing lambda code.

## Create DynamoDB table

We are going to store our data in DynamoDB, NoSQL database provided by Amazon. Let's create table with name `numbers-db` with partition key `id` and sort key `at`.

# Initialize sbt project

Let's create an empty project
```
$ sbt new scala/scala-seed.g8
Minimum Scala build.

name [My Something Project]: scala-aws-lambda

Template applied in ./scala-aws-lambda

$ cd scala-aws-lambda/
```

Add the following to your `project/plugins.sbt` file:

```scala
resolvers += "JBoss" at "https://repository.jboss.org/"

addSbtPlugin("com.gilt.sbt" % "sbt-aws-lambda" % "0.4.2")
```

Add the `AwsLambdaPlugin` auto-plugin and s3-bucket name (the actual lambda binary will be stored there) to your `build.sbt`. We also need additional library dependencies to be able to handle lambda input (`aws-lambda-java-core`).

```scala
enablePlugins(AwsLambdaPlugin)

retrieveManaged := true

s3Bucket := Some("scala-aws-lambda")

awsLambdaMemory := Some(320)

awsLambdaTimeout := Some(30)

libraryDependencies += "com.amazonaws" % "aws-lambda-java-core" % "1.1.0"
```

To create new lambda you need to run the following command:
```
AWS_ACCESS_KEY_ID=<YOUR_KEY_ID> AWS_SECRET_ACCESS_KEY=<YOUR_SECRET_KEY> sbt createLambda
```
To update your lambda you need to run this command:
```
AWS_ACCESS_KEY_ID=<YOUR_KEY_ID> AWS_SECRET_ACCESS_KEY=<YOUR_SECRET_KEY> sbt updateLambda
```

# Post method implementation

First of all, we need to specify which actualy handlers we are going to use. Also we need to use one of the json parser. Let's use `circe`. For all of that we need to update `build.sbt` file.

```scala
lambdaHandlers += "post" -> "com.dbrsn.lambda.Main::post"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-java8"
) map (_ % "0.8.0")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.150"
```

Now we can write our simple main class with post method, which will be called from Amazon Lambda.
We need imports:

```scala
// Amazon AWS DynamoDB
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
// Amazon AWS Lambda
import com.amazonaws.services.lambda.runtime.Context
// Circe encoding/decoding
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.parser._
import io.circe.syntax._
// Other
import org.apache.commons.io.IOUtils
import scala.collection.JavaConverters._
```

Our input order will be the following:

```scala
/**
  * Input order
  */
final case class Order(clientId: ClientId, numbers: Set[Int])

object Order {
  final type ClientId = String
}
```

Our output and persisted order will be:

```scala
/**
  * Output and persisted order
  */
final case class PersistedOrder(orderId: OrderId, at: LocalDateTime, order: Order)

object PersistedOrder {
  final type OrderId = String
}
```

Finally, Main class itself:

```scala
class Main {
  // Initializing DynamoDB client
  lazy val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_EAST_1).build()
  lazy val dynamoDb: DynamoDB = new DynamoDB(client)

  lazy val clock: Clock = Clock.systemUTC()

  val tableName: String = "numbers-db"
  val encoding = "UTF-8"

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
          .withPrimaryKey("id", o.orderId)
          .withLong("at", Timestamp.valueOf(o.at).getTime)
          .withString("clientId", o.order.clientId)
          .withList("numbers", o.order.numbers.toList.asJava)
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
```

# Deploy

And now after running the following command and answering access question, your lambda will be deployed
```
AWS_ACCESS_KEY_ID=<YOUR_KEY_ID> AWS_SECRET_ACCESS_KEY=<YOUR_SECRET_KEY> sbt createLambda
```

You can visit aws console and find your lambda there

![Post lambda available](http://dbrsn.com/resources/2017-06-18-how-to-build-scala-tiny-backend-on-amazon-aws-lambda/Screen-Shot-2017-06-19-at-20.55.11.png "Post lambda available")

Let's try to test it. Go to post function and click test button. We can use following example as an input:
```json
{
  "clientId": "123",
  "numbers": [
    1,
    2,
    3
  ]
}
```
And now we see an error

![DynamoDB authorization error](http://dbrsn.com/resources/2017-06-18-how-to-build-scala-tiny-backend-on-amazon-aws-lambda/Screen-Shot-2017-06-19-at-22.49.16.png "DynamoDB authorization error")

This mean, that our User is not authorized to perform put item action into DynamoDB. Let's authorize him. We need to go to our IAM management console and in the [Roles section](https://console.aws.amazon.com/iam/home?#/roles) select our role (by default, it's `lambda_basic_execution`), click `Attach Policy` button in a `Permissions` section and attach policy `AmazonDynamoDBFullAccess`. Otherwise, you can goo to DynamoDB config and in a tab "Access control" create policy for our user to allow our user to perform action `PutItem`. 

That's all. Now we can try to test it one more time and happily enjoy the following result:

![Post method works](http://dbrsn.com/resources/2017-06-18-how-to-build-scala-tiny-backend-on-amazon-aws-lambda/Screen-Shot-2017-06-19-at-23.04.38.png "Post method works")

We can also see that our DynamoDB database is actually updated:

![DynamoDB items](http://dbrsn.com/resources/2017-06-18-how-to-build-scala-tiny-backend-on-amazon-aws-lambda/Screen-Shot-2017-06-19-at-23.09.27.png "DynamoDB items")

# API Gateway for post method

We created our new shiny lambda function. But it doesn't have any API to connect it to the external world. Let's fix it. Let's go to Amazon AWS API Gateway and create new API method POST. Here you need to specify your concrete lambda method and test it. Surprisely, it works.

# Summary

In this article we created a simple project which allows us to build lambda functions in Scala with automated sbt deployment. We also learnt how to put data to Amazon DynamoDB. And it's easy and powerfull. The source code of this project you can find in my [github](https://github.com/dborisenko/scala-aws-lambda-dynamodb).

# Articles
* [Writing AWS Lambda Functions in Scala](https://aws.amazon.com/blogs/compute/writing-aws-lambda-functions-in-scala/)
* [sbt-aws-lambda](https://github.com/gilt/sbt-aws-lambda/blob/master/README.md)
