import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"
  lazy val awsLambda = "com.amazonaws" % "aws-lambda-java-core" % "1.1.0"
  lazy val awsDynamodb = "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.150"
  lazy val circe = Seq(
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-java8"
  ) map (_ % "0.8.0")
  lazy val apacheCommons = "commons-io" % "commons-io" % "2.5"
}
