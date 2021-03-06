package controllers

import java.util.UUID
import javax.inject.Inject

import io.swagger.annotations.{ApiResponse, ApiResponses, ApiOperation, Api}
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import services.UserService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "/", description = "Operations about authentification",produces="application/json, application/xml")
class Authentification @Inject()(ws: WSClient, userService: UserService) extends Controller {

  @ApiOperation(
    nickname = "connect",
    value = "ask to be connected", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "The request failed, please contact your admin"),
    new ApiResponse(code = 200, message = "The user has been successfully created")))
  def connect(context: String) = Action.async(parse.json) { request =>
    val username: String = (request.body \ "username").as[String]
    val password: String = (request.body \ "password").as[String]

    // Creation du tableau pour le POST vers l'API
    val json: JsValue = Json.obj(
      "agent" -> Json.obj("name" -> "Minecraft", "version" -> 1.8),
      "username" -> username,
      "password" -> password
    )

    val url = "https://authserver.mojang.com/authenticate"
    val futureResponse: Future[WSResponse] = ws.url(url).withHeaders("Content-Type" -> "application/json").post(json)

    futureResponse map {
      case (response) => {
        if (response.status == 200) {
          val id: String     = (response.json \ "selectedProfile" \ "id").as[String]
          val uuid: String   = id.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
          val pseudo: String = (response.json \ "selectedProfile" \ "name").as[String]
          var token: String  = UUID.randomUUID().toString.replaceAll("-", "")
          if (context == "web"){
            token = userService.webAuthentication(UUID.fromString(uuid), token)
          }
          else
            token = userService.minecraftAuthentication(UUID.fromString(uuid), token)

          val result: JsValue = Json.obj(
            "uuid" -> uuid,
            "token" -> token,
            "pseudo" -> pseudo
          )

          request.accepts("application/json") || request.accepts("text/json") match {
            case true => Ok(Json.toJson(result))
            case false => Ok(<credentials><uuid>{uuid}</uuid> <token>{token}</token> <pseudo>{pseudo}</pseudo></credentials>)
          }

        } else {
          BadRequest("The request failed, please contact your admin :" + response.body)
        }
      }
    }
  }

  @ApiOperation(
    nickname = "disconnect",
    value = "ask to be disconnect", httpMethod = "POST")
  @ApiResponses(Array(new ApiResponse(code = 200, message = "Disconnection Done")))
  def disconnect(context: String) = Action(parse.json) { request =>
    val uuid: String = (request.body \ "uuid").as[String]

    if (context == "web") userService.webDisconnection(UUID.fromString(uuid))
    else
      userService.minecraftDisconnection(UUID.fromString(uuid))

    Ok("Disconnection Done")
  }
}