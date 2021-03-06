package com.vandyapps.pubserver

import util.Try
import com.twitter.finatra._

object PubApp extends App {

  println("================================")
  println("=          PUB SERVER          =")
  println("================================")
  println()

  this.args match {
    case Array("help") | Array() => println(usageGuide)
    case Array(key)              => bootServer(key)
    case Array(key, port)        => bootServer(key, port)
    case Array(key, port, env)   => bootServer(key, port, env)
  }

  def bootServer(
      key:  String,
      port: String = "8080", 
      env:  String = "development") {    
    System.setProperty("com.twitter.finatra.config.env", env)
    System.setProperty("com.twitter.finatra.config.port", s":$port")
    System.setProperty("com.twitter.finatra.config.adminPort", "")
    System.setProperty("com.twitter.finatra.config.appName", "Truss")    
    
    println(s"Starting $env server on port $port")
    println(s"With password: $key")
    
    val validator = new RequestValidation { def apiKey = key }
    
    val server = new FinatraServer
    server.register(new MainController(validator))
    server.start()
  }
  
  lazy val usageGuide =
    io.Source.fromInputStream(getClass.getResourceAsStream("usage.txt")).mkString

}

class MainController(validator: RequestValidation) extends Controller
    with OrderRegister {
  import MainController._
  import validator._

  get("/") { request =>
    render.static("index.txt").toFuture
  }

  get("/order") { request =>
    (for {
      countStr <- request.params.get("count");
      count    <- Try(countStr.toInt).toOption
    } yield PubReport(status = "Okay",
                      orders = getOrders(count)))
        .getOrElse(PubReport(status = "Malformed request"))
        .>>>(render.json(_).toFuture)
  }

  post("/order") { request =>
    (for {
      orderStr <- request.params.get("orderNumber");
      apikey   <- request.params.get("apikey");
      orderNum <- Try(orderStr.toInt).toOption;
      if isValid(apikey, orderNum)
    } yield orderNum)
        .map { num =>
            addOrder(num)
            PubReport(status = "Successfully added order #" + num) }
        .getOrElse(PubReport(status = "Malformed request"))
        .>>>(render.json(_).toFuture)
  }

  get("/console") { request =>
    render.static("main.html").toFuture
  }

  notFound { request =>
    render.json(PubReport(status = "Wrong API call")).toFuture
  }

  error { request =>
    render.json(PubReport(status = s"Something's wrong: ${request.error}")).toFuture
  }

}

object MainController {
  implicit class Piped[T](pipee: T) {
    def >>>[U](receiver: T=>U): U = receiver(pipee)
  }
}
