import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, OK, NoContent}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import model.UserHandler
import model.UserHandler._
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class UserPwd(pwd:String)

case class UpsertRequest(username:String, password:String )


trait Protocols extends DefaultJsonProtocol {
  implicit val delUserFormat = jsonFormat1(UserDeleted.apply)
  implicit val uNotFoundFormat = jsonFormat1(UserNotFound.apply)
  implicit val idFormat = jsonFormat1(UserPwd.apply)
  implicit val usrFormat = jsonFormat2(User.apply)
  implicit val userLogin = jsonFormat2(UpsertRequest.apply)
}

trait Service extends Protocols {

  import scala.concurrent.duration._

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  def config: Config

  val logger: LoggingAdapter

  def userHandler: ActorRef

  implicit def requestTimeout = Timeout(5 seconds)

  def userAuthenticate(credentials: Credentials): Future[Option[UserPwd]] = {
    credentials match {
      case p@Credentials.Provided(userName) =>
        fetchUserId(userName).map {
          case Some(UserPwd(id)) if p.verify(id) =>
            Some(UserPwd(id))
          case _ => None

        }
      case _ =>
        Future.successful(None)
    }

  }

  def fetchUserId(userName: String): Future[Option[UserPwd]] = {

    (userHandler ? GetUser(userName)).map {
      case User(_, p) => Some(UserPwd(p))
      case _ => None
    }
  }

  val unsecuredRoutes: Route = {
    pathPrefix("api") {
      pathPrefix("user") {
        path("register") {
          put {
            entity(as[UpsertRequest]) { u =>
              complete {
                (userHandler ? UserHandler.Register(u.username, u.password)).map {
                  case true => OK -> s"Thank you ${u.username}"
                  case _ => InternalServerError -> "Failed to complete your request. please try later"
                }
              }
            }
          }
        }
      }
    }
  }
  val routes =
    logRequestResult("akka-http-secured-service") {
      authenticateBasicAsync(realm = "secure site", userAuthenticate) { user =>
        pathPrefix("user") {
          path("add") {
            put {
              entity(as[UpsertRequest]) { u =>
                complete {
                  (userHandler ? Register(u.username, u.password)).mapTo[User]
                }
              }
            }
          } ~
            path(Segment) { id =>
              get {
                complete {
                  (userHandler ? GetUser(id)).mapTo[User]
                }
              }
            } ~
            path(Segment) { id =>
              post {
                entity(as[UpsertRequest]) { u =>
                  complete {
                    (userHandler ? UserHandler.Update(u.username, u.password)).map {
                      case false => InternalServerError -> s"Could not update user $id"
                      case _ => NoContent -> ""
                    }
                  }
                }
              }
            } ~
            path(Segment) { id =>
              delete {
                complete {
                  (userHandler ? UserHandler.DeleteUser(id)).map {
                    case UserNotFound(uname) => InternalServerError -> s"Could not delete user $uname"
                    case u: UserDeleted => NoContent -> ""
                  }
                }
              }
            }
        }
      }
    }
}


object AkkaHttpRedisService extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)
  val userHandler = system.actorOf(UserHandler.props)

  Http().bindAndHandle(unsecuredRoutes ~ routes , config.getString("http.interface"), config.getInt("http.port"))
}
