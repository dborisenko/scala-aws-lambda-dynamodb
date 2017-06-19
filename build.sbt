import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.dbrsn.lambda",
      scalaVersion := "2.12.2",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "lambda",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += awsLambda,
    libraryDependencies += awsDynamodb,
    libraryDependencies += apacheCommons,
    libraryDependencies ++= circe,
    retrieveManaged := true,
    s3Bucket := Some("scala-aws-lambda"),
    awsLambdaMemory := Some(192),
    awsLambdaTimeout := Some(30),
    lambdaHandlers += "post" -> "com.dbrsn.lambda.Main::post"
  )
  .enablePlugins(AwsLambdaPlugin)
