import zio._
import zio.http._
import zio.json._
import zio.test._

object RoutesSpec extends ZIOSpecDefault {

  private def mkApp(initial: Vector[Whisper]) =
    for {
      db  <- Ref.make(initial)
      hub <- Hub.unbounded[Whisper]
      app  = Main.routes(db, hub)
               .handleError(e => Response.internalServerError(s"Erreur: ${e.getMessage}"))
               .toHttpApp
    } yield (app, db)

  private def url(path: String): URL =
    URL.decode(path).toOption.getOrElse(throw new RuntimeException(s"Invalid URL: $path"))

  override def spec: Spec[TestEnvironment, Any] =
    suite("HTTP routes")(
      test("GET /ping returns pong") {
        for {
          t    <- mkApp(Vector.empty)
          app   = t._1
          res      <- app.runZIO(Request.get(url("/ping")))
          body     <- res.body.asString
        } yield assertTrue(res.status == Status.Ok, body == "pong")
      },
      test("POST /whisper appends to db") {
        val payload = CreateWhisper("Alice").toJson
        for {
          t    <- mkApp(Vector.empty)
          app   = t._1
          db    = t._2
          res <- app.runZIO(
                   Request
                     .post(url("/whisper"), Body.fromString(payload))
                     .addHeader(Header.ContentType(MediaType.application.json))
                 )
          items <- db.get
        } yield assertTrue(res.status == Status.Ok, items.map(_.content) == Vector("Alice"))
      },
      test("GET /whispers returns JSON list") {
        val initial = Vector(Whisper("A", 1L), Whisper("B", 2L))
        for {
          t    <- mkApp(initial)
          app   = t._1
          res      <- app.runZIO(Request.get(url("/whispers")))
          body     <- res.body.asString
          decoded   = body.fromJson[Vector[Whisper]]
        } yield assertTrue(res.status == Status.Ok, decoded == Right(initial))
      },
      test("GET /whisper/:index returns 404 when out of range") {
        for {
          t    <- mkApp(Vector.empty)
          app   = t._1
          res      <- app.runZIO(Request.get(url("/whisper/0")))
        } yield assertTrue(res.status == Status.NotFound)
      },
      test("PUT /whisper/:index updates element") {
        val initial = Vector(Whisper("Old", 1L))
        val payload = CreateWhisper("New").toJson
        for {
          t    <- mkApp(initial)
          app   = t._1
          db    = t._2
          res <- app.runZIO(
                   Request
                     .put(url("/whisper/0"), Body.fromString(payload))
                     .addHeader(Header.ContentType(MediaType.application.json))
                 )
          items <- db.get
        } yield assertTrue(res.status == Status.Ok, items.headOption.map(_.content) == Some("New"))
      },
      test("DELETE /whisper/:index removes element") {
        val initial = Vector(Whisper("A", 1L), Whisper("B", 2L))
        for {
          t    <- mkApp(initial)
          app   = t._1
          db    = t._2
          res       <- app.runZIO(Request.delete(url("/whisper/0")))
          items     <- db.get
        } yield assertTrue(res.status == Status.Ok, items.map(_.content) == Vector("B"))
      },
      test("GET /random returns element with TestRandom") {
        val initial = Vector(Whisper("A", 1L), Whisper("B", 2L))
        for {
          _        <- TestRandom.feedInts(1)
          t    <- mkApp(initial)
          app   = t._1
          res      <- app.runZIO(Request.get(url("/random")))
          body     <- res.body.asString
          decoded   = body.fromJson[Whisper]
        } yield assertTrue(res.status == Status.Ok, decoded.map(_.content) == Right("B"))
      }
    )
}
