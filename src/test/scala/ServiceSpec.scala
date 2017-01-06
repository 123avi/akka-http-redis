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

import akka.actor.ActorSystem
import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge}
import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.RouteResult.Rejected
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestActorRef
import model.UserHandler
import model.UserHandler.{User, UserDeleted, UserNotFound}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import redis.RedisClient
import reposiory.{ConcreteRedis, RedisRepo}

import scala.concurrent.Await
import scala.concurrent.duration._

class ServiceSpec extends FlatSpec
  with Matchers
  with ScalatestRouteTest
  with Service
  with ScalaFutures
  with BeforeAndAfterEach{
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5 seconds)
  override def testConfigSource = "akka.loglevel = DEBUG"
  override def config = testConfig
  override val logger = NoLogging
  val userHandler: TestActorRef[UserHandler] = TestActorRef[UserHandler](new UserHandler())

  val repo  =new RedisRepo with ConcreteRedis {
    override val db = RedisClient(host = redisUrl.getHost, port = redisUrl.getPort, password = pwd)
  }

  val validUser = "mike"
  val pwd = "stupid_password"

  val userCredentials = BasicHttpCredentials(validUser,pwd)
  def addUser(): Unit = {
    Await.ready(repo.upsert(validUser, pwd), 2 seconds)
    whenReady(repo.get(validUser)){ res =>
      res shouldEqual Some(pwd)
    }
  }

  override def beforeEach: Unit = {
    addUser()
  }

  "Registration Service" should "add user" in {
    val uname = "newuser"
    val upwd = "123pwd"
    Await.ready(repo.del(validUser), 2 seconds)

      Put(s"/api/user/register", UpsertRequest(uname, upwd)) ~> unsecuredRoutes ~> check {
        status shouldBe OK
        responseAs[String] shouldBe s"Thank you $uname"
      }
    }

  "Secured service" should "get user" in {

    Get(s"/user/$validUser") ~> addCredentials(userCredentials) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[User] shouldBe User(validUser,pwd)
    }

  }

  it should "delete user" in {
    val uname = "newuser"
    val upwd = "123pwd"
    Await.ready(repo.upsert(uname, upwd), 2 seconds)
    whenReady(repo.get(uname)){ res =>
      res shouldEqual Some(upwd)
    }

    Delete(s"/user/$uname") ~> addCredentials(userCredentials) ~> routes ~> check {
      status shouldBe NoContent
    }
  }

  it should "faile to delete user non existing user" in {
    val invalidUser = "unknownUser"

    Delete(s"/user/$invalidUser") ~> addCredentials(userCredentials) ~> routes ~> check {
      status shouldBe InternalServerError
      responseAs[String] shouldBe s"Could not delete user $invalidUser"
    }
  }

  it should "update user" in {
    val p = "blabla"
    Post(s"/user/$validUser", UpsertRequest(validUser, p)) ~> addCredentials(userCredentials) ~> routes ~> check {
      status shouldBe NoContent
    }
    whenReady(repo.get(validUser)){ res =>
      res shouldEqual Some(p)
    }
    addUser()
  }


  it should "reject request with missing credentials " in {
    Get("/user/blalbs/asdfg") ~> routes ~> check {
      rejections should contain(AuthenticationFailedRejection(CredentialsMissing, HttpChallenge("Basic", "secure site")))
    }
  }

  it should "reject request with bad password - CredentialsRejected" in {
    val falseCredntials = BasicHttpCredentials(validUser,"badpwd")
    Get("/user/blalbs/asdfg") ~> addCredentials(falseCredntials) ~> routes ~> check {
      rejections should contain(AuthenticationFailedRejection(CredentialsRejected, HttpChallenge("Basic", "secure site")))
    }
  }
}
