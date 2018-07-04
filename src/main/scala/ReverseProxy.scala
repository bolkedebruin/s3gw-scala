import java.util.concurrent.TimeUnit

import lol.http._

import scala.concurrent.ExecutionContext.Implicits.global
import monix.execution.Scheduler.{global => scheduler}

object ReverseProxy {
  val AUTHORIZATION = h"Authorization"
  val FORWARDED_FOR = h"X-Forwarded-For"

  val Forbidden = Response(403)

  def getAccessKey(headers: Map[HttpString, HttpString]): Option[String] = {
    val header = headers.get(AUTHORIZATION)
    if (header.isEmpty) {
      None
    } else {
      val h = header.get.toString()
      Some(h.substring(h.indexOf("Credential") + 11, h.indexOf(",") - 1).split("/")(0))
    }
  }

  def forwarders(headers: Map[HttpString, HttpString]): Option[Array[String]] = {
    val header = headers.get(FORWARDED_FOR)
    if (header.isEmpty) {
      None
    } else {
      Some(header.get.toString().split(","))
    }
  }

  def main(args: Array[String]): Unit = {
    val client = Client("127.0.0.1", 10080, "http")
    val authorizer = RangerS3Authorizer
    val s3Provider = new RgwUserProvider("accesskey", "secretkey", "http://127.0.0.1:10080/admin")

    val c = scheduler.scheduleWithFixedDelay(0, 30, TimeUnit.SECONDS,
      new Runnable {
        override def run(): Unit = {
          s3Provider.sync()
        }
      }
    )

    Server.listen(10081) {
      case request: Request =>
        val s3req = S3Request()

        val accessKey: Option[String] = getAccessKey(request.headers)

        // FIXME: make sure to break if empty
        if (accessKey.isEmpty) {
          Forbidden("Access denied")
        } else {
          s3req.username = s3Provider.getUser(accessKey.get)
        }

        println(request.path)
        println(request.from)


        val fwds = forwarders(request.headers).getOrElse(Array[String]())

        s3req.path = request.path
        if (!request.from.isEmpty) {
          s3req.remoteAddr = request.from.get.getHostAddress
        }
        s3req.fwdAddresses = fwds

        if (fwds.size < 1) {
          s3req.clientIp = s3req.remoteAddr
          request.addHeaders(FORWARDED_FOR -> HttpString(request.from.toString))
        } else {
          fwds :+ request.from.toString
          request.addHeaders(FORWARDED_FOR -> HttpString(fwds.mkString(",")))
          s3req.clientIp = fwds(1)
        }

        s3req.method = request.method.toString()
        s3req.username = "ceph-admin"

        println(" CLIENT IP " + s3req.clientIp + " REMOTE: " + s3req.remoteAddr + " PATH: " + request.path + " QUERY: " + request.url)
        val response = request.method match {
          case GET =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
          case HEAD =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
          case PUT =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
          case POST =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
          case DELETE =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
          case _ =>
            s3req.accessType = RangerS3Authorizer.READ
            if (authorizer.checkPermission(s3req)) {
              client {
                request
              }
            } else {
              pureResponse(Forbidden("Access denied"))
            }
        }
        response
    }

    println("Proxy started")
  }
}
