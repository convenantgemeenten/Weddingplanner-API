package weddingplanner.endpoint

import java.time.Instant

import cats.effect.IO
import io.finch.{Endpoint, Ok}
import lspace._
import lspace.ns.vocab.schema
import lspace.provider.mem.MemGraph
import lspace.structure.{ClassType, Graph}
import monix.eval.Task
import monix.reactive.Observable
import shapeless.{HList, HNil}
import scribe._
import weddingplanner.ns.{agenda, Agenda, Appointment}

import scala.util.{Failure, Success, Try}

case class PlannerEndpoint[Json](agendaGraph: Graph, personGraph: Graph, placeGraph: Graph, appointmentGraph: Graph)(
    implicit ndecoder: lspace.codec.NativeTypeDecoder.Aux[Json],
    nencoder: lspace.codec.NativeTypeEncoder.Aux[Json])
    extends Endpoint.Module[IO] {
  val agendaService      = OntologyEndpoint(agendaGraph, Agenda.ontology)
  val personService      = OntologyEndpoint(personGraph, schema.Person)
  val placeService       = OntologyEndpoint(placeGraph, schema.Place)
  val appointmentService = OntologyEndpoint(appointmentGraph, Appointment)

  implicit val ec = monix.execution.Scheduler.global

  import io.finch._
  import lspace.Implicits.AsyncGuide.guide

  implicit val dateTimeDecoder: DecodeEntity[Instant] =
    DecodeEntity.instance(s =>
      Try(Instant.parse(s)) match {
        case Success(instant) => Right(instant)
        case Failure(error)   => Left(error)
    })

  implicit class WithGraphTask(graphTask: Task[Graph]) {
    def ++(graph: Graph) =
      for {
        graph0 <- graphTask
        graph1 <- graph0 ++ graph
      } yield graph1
  }
  def allGraph: Task[Graph] = MemGraph("allgraph") ++ agendaGraph ++ personGraph ++ placeGraph ++ appointmentGraph

  val placesAvailable: Endpoint[IO, String] = {
    import shapeless.::
    get("place" :: "available" :: param[Instant]("since") :: param[Instant]("until"))
      .mapOutputAsync {
        case (since: Instant) :: (until: Instant) :: HNil =>
          (for {
            graph <- allGraph
            placesAndAppointments <- g.N
              .hasLabel(Agenda.ontology)
              .project()
              .by(_.out(agenda).out(Agenda.keys.appointment).hasLabel(Appointment.ontology))
              .withGraph(graph)
              .toListF
              .map(_.groupBy(_._1)
                .mapValues(_.map(_._2)))
          } yield {
            //        placesAndAppointments.filter {
            //          case (place, appointments) =>
            //            appointments.filter(_.out(Appointment.keys.s)
            //        }

            Ok("")
          }).to[IO]
      }

  }

  val api = "planner" :: placesAvailable
}
