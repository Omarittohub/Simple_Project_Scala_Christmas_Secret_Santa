import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.http.Middleware
import zio.json._
import zio.stream._
import java.time.Instant

//Les modeles de donnees
case class CreateWhisper(content: String)
object CreateWhisper {
  implicit val codec: JsonCodec[CreateWhisper] = DeriveJsonCodec.gen
}

case class Whisper(content: String, timestamp: Long)
object Whisper {
  implicit val codec: JsonCodec[Whisper] = DeriveJsonCodec.gen
}

//L'appli principale
object Main extends ZIOAppDefault {

  def run =
    for {
      db  <- Ref.make(Vector(Whisper("Seconde Chance", Instant.now.getEpochSecond)))
      hub <- Hub.unbounded[Whisper]
      app = (routes(db, hub) @@ Middleware.cors) 
      .handleError(e => Response.internalServerError(s"Erreur: ${e.getMessage}") ) 
      .toHttpApp

      _ <- Server.serve(app).provide(Server.default)
    } yield ()


  def routes(db: Ref[Vector[Whisper]], hub: Hub[Whisper]) = Routes(

    // ping-pong (check que le serveur tourne)
    Method.GET / "ping" -> handler(Response.text("pong")),

    // obtenir un secret aleatoire de la bdd
    Method.GET / "random" -> handler {
      for {
        items <- db.get
        secret <- Random.nextIntBounded(items.size).map(items(_))
      } yield Response.json(secret.toJson)
    },

    // creer un nouveau secret et le mettre dans la bdd
    // prends un JSON avec le contenu du secret
    // Json: { "content": "mon secret" }
    // retourne 200 OK si reussi
    Method.POST / "whisper" -> handler { (req: Request) =>
      req.body.asString.flatMap { body =>
        body.fromJson[CreateWhisper] match {
          case Left(err) => ZIO.succeed(Response.badRequest(err))
          case Right(input) =>
            val newWhisper = Whisper(input.content, Instant.now.getEpochSecond)
            db.update(_ :+ newWhisper) *>
            hub.publish(newWhisper) *>
            ZIO.succeed(Response.ok)
        }
      }
    },

    // Lire les noms
    // retourne un JSON avec la liste des noms
    // Json: [ { "content": "nom1", "timestamp": 1234567890 }, { "content": "nom2", "timestamp": 1234567890 } ]
    // timestamp est pour tracker quand le nom a ete ajoute
    // utile pour le front-end pour afficher l'ordre d'ajout
    Method.GET / "whispers" -> handler {
      db.get.map(ws => Response.json(ws.toJson))
    },

    // lire un nom par index
    // retourne 404 si l'index est hors limite
    // index commence a 0 (changes dynamically en fonction du nombre de noms et change selon les ajouts/suppressions)
    Method.GET / "whisper" / int("index") -> handler { (index: Int, _: Request) =>
      db.get.map { ws =>
        if (index < 0 || index >= ws.size)
          Response.status(Status.NotFound)
        else
          Response.json(ws(index).toJson)
      }
    },

    // changer un nom par index
    // prends un JSON avec le nouveau contenu du nom
    // Json: { "content": "nouveau nom" }
    Method.PUT / "whisper" / int("index") -> handler { (index: Int, req: Request) =>
      req.body.asString.flatMap { body =>
        body.fromJson[CreateWhisper] match {
          case Left(err) =>
            ZIO.succeed(Response.badRequest(err))
          case Right(input) =>
            db.modify { ws =>
              if (index < 0 || index >= ws.size)
                (Response.status(Status.NotFound), ws)
              else {
                val updated = ws.updated(index, ws(index).copy(content = input.content))
                (Response.text("Nom updated"), updated)
              }
            }
        }
      }
    } ,

    // enlever un nom par index
    Method.DELETE / "whisper" / int("index") -> handler { (index: Int, _: Request) =>
      db.modify { ws =>
        if (index < 0 || index >= ws.size)
          (Response.status(Status.NotFound), ws)
        else
          (Response.text("Nom deleted"), ws.patch(index, Nil, 1))
      }
    },



    // la WBS pour strean les messages
    Method.GET / "stream" -> handler {
      val socket = Handler.webSocket { channel =>
        ZStream.fromHub(hub)
          .map(w => WebSocketFrame.text(w.toJson))
          .map(frame => Read(frame))
          .foreach(channel.send)
      }
      socket.toResponse
    }
  )
}
