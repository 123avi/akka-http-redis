// See the LICENCE.txt file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package model

import akka.actor.{Actor, ActorLogging, Props}
import redis.RedisClient
import reposiory.{ConcreteRedis, RedisRepo}
import akka.pattern.pipe

trait UserRepoSupport extends RedisRepo with ConcreteRedis {
  this: Actor =>
  import context.system
  val db = RedisClient(host = redisUrl.getHost, port = redisUrl.getPort, password = pwd)

}

object UserHandler {
  def props: Props = Props(new UserHandler)

  case class User(username: String, details: String)
  case class Register(username: String, password: String)
  case class Update(username: String, details: String)
  case class GetUser(username: String)
  case class DeleteUser(username: String)
  case class UserNotFound(username: String)
  case class UserDeleted(username: String)

}

class UserHandler extends Actor with ActorLogging with UserRepoSupport{
  import UserHandler._
  implicit val ec = context.dispatcher
  override def receive: Receive = {
    case Register(id, pwd) =>
      upsert(id, pwd) pipeTo sender()

    case Update(id, details) =>
      upsert(id, details) pipeTo sender()

    case GetUser(uname) =>
      //closing over the sender in Future is not safe
      //http://helenaedelson.com/?p=879
      val _sender = sender()
      get(uname).foreach{
        case Some(i) => _sender ! User(uname, i)
        case None => _sender ! UserNotFound
      }

    case DeleteUser(uname) =>
      val _sender = sender()
      del(uname).foreach{
        case i if i > 0 => _sender ! UserDeleted(uname)
        case _ => _sender ! UserNotFound(uname)
      }
  }

}
