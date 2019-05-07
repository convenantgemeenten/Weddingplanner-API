package weddingplanner.endpoint

import com.twitter.finagle.http.Status
import io.finch.Input
import lspace.codec.ActiveContext
import lspace.ns.vocab.schema
import lspace.provider.mem.MemGraph
import lspace.services.codecs.{Application => LApplication}
import lspace.structure.Graph
import lspace.util.SampleGraph
import monix.eval.Task
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, FutureOutcome, Matchers}
import weddingplanner.ns.PartnerTest

class PartnerTestEndpointSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  import lspace.Implicits.Scheduler.global
  import lspace.encode.EncodeJsonLD._
  import lspace.services.codecs.Encode._

  lazy val sampleGraph: Graph     = MemGraph("ApiServiceSpec")
  implicit val nencoder           = lspace.codec.argonaut.NativeTypeEncoder
  implicit val encoder            = lspace.codec.jsonld.Encoder(nencoder)
  implicit val ndecoder           = lspace.codec.argonaut.NativeTypeDecoder
  implicit lazy val activeContext = PartnerTestEndpoint.activeContext

  val partnerService = PartnerTestEndpoint(sampleGraph)

  lazy val initTask = (for {
    sample <- SampleGraph.loadSocial(sampleGraph)
    _ <- for {
      _ <- sample.persons.Gray.person --- schema.spouse --> sample.persons.Levi.person
    } yield ()
  } yield sample).memoizeOnSuccess

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    new FutureOutcome(initTask.runToFuture flatMap { result =>
      super.withFixture(test).toFuture
    })
  }
  "A PartnerEndpoint" should {
    "test positive for a spouse-relation for Gray or Levi" in {
      (for {
        sample <- initTask
        gray = sample.persons.Gray.person
        levi = sample.persons.Levi.person
        test = PartnerTest(Set(gray.iri, levi.iri))
        node <- test.toNode
      } yield {
        val input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        partnerService
          .partner(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test positive for a spouse-relation for Gray or Stan" in {
      (for {
        sample <- initTask
        gray = sample.persons.Gray.person
        stan = sample.persons.Stan.person
        test = PartnerTest(Set(gray.iri, stan.iri))
        node <- test.toNode
      } yield {
        val input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        partnerService
          .partner(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a spouse-relation for Stan or Garrison" in {
      (for {
        sample <- initTask
        stan     = sample.persons.Stan.person
        garrison = sample.persons.Garrison.person
        test     = PartnerTest(Set(stan.iri, garrison.iri))
        node <- test.toNode
      } yield {
        val input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        partnerService
          .partner(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
  }
  "A compiled PartnerEndpoint" should {
    "test positive for a spouse-relation for Gray or Levi" in {
      (for {
        sample <- initTask
        gray = sample.persons.Gray.person
        levi = sample.persons.Levi.person
        test = PartnerTest(Set(gray.iri, levi.iri))
        node <- test.toNode
        input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        _ <- Task.fromIO(
          partnerService
            .compiled(input.request)
            .map {
              case (t, Left(e)) => fail()
              case (t, Right(r)) =>
                r.status shouldBe Status.Ok
                r.contentString shouldBe "true"
            })
      } yield succeed).runToFuture
    }
    "test positive for a spouse-relation for Gray or Stan" in {
      (for {
        sample <- initTask
        gray = sample.persons.Gray.person
        stan = sample.persons.Stan.person
        test = PartnerTest(Set(gray.iri, stan.iri))
        node <- test.toNode
        input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        _ <- Task.fromIO(
          partnerService
            .compiled(input.request)
            .map {
              case (t, Left(e)) => fail()
              case (t, Right(r)) =>
                r.status shouldBe Status.Ok
                r.contentString shouldBe "true"
            })
      } yield succeed).runToFuture
    }
    "test negative for a spouse-relation for Stan or Garrison" in {
      (for {
        sample <- initTask
        stan     = sample.persons.Stan.person
        garrison = sample.persons.Garrison.person
        test     = PartnerTest(Set(stan.iri, garrison.iri))
        node <- test.toNode
        input = Input
          .post("/partner")
          .withBody[LApplication.JsonLD](node)
        _ <- Task.fromIO(
          partnerService
            .compiled(input.request)
            .map {
              case (t, Left(e)) => fail()
              case (t, Right(r)) =>
                r.status shouldBe Status.Ok
                r.contentString shouldBe "false"
            })
      } yield succeed).runToFuture
    }
  }
}
