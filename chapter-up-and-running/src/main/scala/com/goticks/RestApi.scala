package com.goticks

import javax.ws.rs.Path

import akka.actor._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.pattern.ask
import akka.util.Timeout
import com.goticks.BoxOffice.Events
import io.swagger.annotations._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import akka.http.scaladsl.model.HttpMethods._

import scala.concurrent.ExecutionContext

class RestApi(system: ActorSystem, timeout: Timeout)
    extends RestRoutes {
  implicit val requestTimeout = timeout
  // maybe we could add some configuration here
  implicit def executionContext = system.dispatcher

  def createBoxOffice = system.actorOf(BoxOffice.props, BoxOffice.name)

  val settings = CorsSettings.defaultSettings.copy(allowedMethods = List(GET, POST, DELETE))
  val routes: Route = cors(settings) {
    eventsRoute ~ eventRoute ~ ticketsRoute ~ new SwaggerDocService(system).routes
  }
}


@Api(value = "/events", description = "manage the ticket events", produces = "application/json")
@Path("/events")
trait RestRoutes extends BoxOfficeApi
    with EventMarshalling {
  import StatusCodes._

  @ApiOperation(value = "Look up the events", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return the events", response = classOf[Events])
  ))
  def eventsRoute =
    pathPrefix("events") {
      pathEndOrSingleSlash {
        get {
          // GET /events
          onSuccess(getEvents()) { events =>
            complete(OK, events)
          }
        }
      }
    }

  def eventRoute =
    pathPrefix("events" / Segment) { event =>
      pathEndOrSingleSlash {
        postEventRoute(event) ~
        getEventRoute(event) ~
        deleteEventRoute(event)
      }
    }

  @Path("/{event}")
  @ApiOperation(value = "Delete the event", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "event", value = "the name of the event", required = true, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return the event that is deleted", response = classOf[BoxOffice.Event]),
    new ApiResponse(code = 404, message = "The event is not found.")
  ))
  def deleteEventRoute(event: String) = {
    delete {
      // DELETE /events/:event
      onSuccess(cancelEvent(event)) {
        _.fold(complete(NotFound))(e => complete(OK, e))
      }
    }
  }

  @Path("/{event}")
  @ApiOperation(value = "Get the event", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "event", value = "the name of the event", required = true, dataType = "string", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return the event that is found", response = classOf[BoxOffice.Event]),
    new ApiResponse(code = 404, message = "The event is not found.")
  ))
  def getEventRoute(event: String) = {
    get {
      // GET /events/:event
      onSuccess(getEvent(event)) {
        _.fold(complete(NotFound))(e => complete(OK, e))
      }
    }
  }

  @Path("/{event}")
  @ApiOperation(value = "Create the event", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "event", value = "the name of the event", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value="the event description", required = true, dataType = "com.goticks.EventDescription", paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return the event That created", response = classOf[BoxOffice.Event]),
    new ApiResponse(code = 400, message = "The event exists already.")
  ))
  def postEventRoute(event: String) = {
    post {
      // POST /events/:event
      entity(as[EventDescription]) { ed =>
        onSuccess(createEvent(event, ed.tickets)) {
          case BoxOffice.EventCreated(event) => complete(Created, event)
          case BoxOffice.EventExists =>
            val err = Error(s"$event event exists already.")
            complete(BadRequest, err)
        }
      }
    }
  }

  @Path("/{event}/tickets")
  @ApiOperation(value = "Create the event", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "event", value = "the name of the event", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value="the ticket request", required = true, dataType = "com.goticks.TicketRequest", paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return the event That created", response = classOf[BoxOffice.Event]),
    new ApiResponse(code = 400, message = "The event exists already.")
  ))
  def ticketsRoute =
    pathPrefix("events" / Segment / "tickets") { event =>
      post {
        pathEndOrSingleSlash {
          // POST /events/:event/tickets
          entity(as[TicketRequest]) { request =>
            onSuccess(requestTickets(event, request.tickets)) { tickets =>
              if(tickets.entries.isEmpty) complete(NotFound)
              else complete(Created, tickets)
            }
          }
        }
      }
    }

}


trait BoxOfficeApi {
  import BoxOffice._

  def createBoxOffice(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  lazy val boxOffice = createBoxOffice()

  def createEvent(event: String, nrOfTickets: Int) =
    boxOffice.ask(CreateEvent(event, nrOfTickets))
      .mapTo[EventResponse]

  def getEvents() =
    boxOffice.ask(GetEvents).mapTo[Events]

  def getEvent(event: String) =
    boxOffice.ask(GetEvent(event))
      .mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice.ask(CancelEvent(event))
      .mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice.ask(GetTickets(event, tickets))
      .mapTo[TicketSeller.Tickets]
}
//
