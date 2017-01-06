package reposiory

import java.net.URI

import scala.util.Try

trait ConcreteRedis {
  val redisUrl =  URI.create("http://127.0.0.1:6379")
  val pwd   = Try(redisUrl.getUserInfo.split(":")(1)).toOption
}
