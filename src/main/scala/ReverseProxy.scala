import java.io.StringWriter
import java.util.Date
import java.util.concurrent.TimeUnit

import com.amazonaws.services.securitytoken.model.{AssumeRoleWithWebIdentityResult, AssumedRoleUser, Credentials}
import javax.xml.stream.XMLOutputFactory
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
        println(request.content)

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
            if (request.path.startsWith("/sts")) {
              println("Yes!")
              val id = new AssumeRoleWithWebIdentityResult()
              pureResponse(Ok("ok!"))
            } else {
              s3req.accessType = RangerS3Authorizer.READ
              if (authorizer.checkPermission(s3req)) {
                client {
                  request
                }
              } else {
                pureResponse(Forbidden("Access denied"))
              }
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
            if (request.path.startsWith("/sts")) {
              val writer = new StringWriter()
              val xof = XMLOutputFactory.newInstance()
              val xmlsw = xof.createXMLStreamWriter(writer)
              val id = new AssumeRoleWithWebIdentityResult()

              val creds = new Credentials().withExpiration(new Date)
              id.setCredentials(creds)
              id.setAssumedRoleUser(new AssumedRoleUser)

              id.setSubjectFromWebIdentityToken("IMTHESUBJECT")
              id.setAudience("MYAUDIENCE")

              xmlsw.setDefaultNamespace("https://sts.amazonaws.com/doc/2011-06-15/")
              xmlsw.writeStartDocument("UTF-8", "1.0")
              xmlsw.writeStartElement("AssumeRoleWithWebIdentityResponse")

              xmlsw.writeStartElement("AssumeRoleWithWebIdentityResult")

              // subject
              xmlsw.writeStartElement("SubjectFromWebIdentityToken")
              xmlsw.writeCharacters(id.getSubjectFromWebIdentityToken)
              xmlsw.writeEndElement()

              // audience
              xmlsw.writeStartElement("Audience")
              xmlsw.writeCharacters(id.getAudience)
              xmlsw.writeEndElement()

              // AssumedRoleUser
              xmlsw.writeStartElement("AssumedRoleUser")

              xmlsw.writeStartElement("Arn")
              xmlsw.writeCharacters(id.getAssumedRoleUser.getArn)
              xmlsw.writeEndElement()

              xmlsw.writeStartElement("AssumedRoleId")
              xmlsw.writeCharacters(id.getAssumedRoleUser.getAssumedRoleId)
              xmlsw.writeEndElement()

              // end assumedroleuser
              xmlsw.writeEndElement()

              // credentials
              xmlsw.writeStartElement("Credentials")

              xmlsw.writeStartElement("SessionToken")
              xmlsw.writeCharacters(id.getCredentials.getSessionToken)
              xmlsw.writeEndElement()

              xmlsw.writeStartElement("SecretAccessKey")
              xmlsw.writeCharacters(id.getCredentials.getSecretAccessKey)
              xmlsw.writeEndElement()

              xmlsw.writeStartElement("AccessKey")
              xmlsw.writeCharacters(id.getCredentials.getAccessKeyId)
              xmlsw.writeEndElement()

              xmlsw.writeStartElement("Expiration")
              xmlsw.writeCharacters(id.getCredentials.getExpiration.toString) // might need formatter
              xmlsw.writeEndElement()

              // end credentials
              xmlsw.writeEndElement()

              xmlsw.writeStartElement("Provider")
              xmlsw.writeCharacters("KeyCloak") // might need formatter
              xmlsw.writeEndElement()

              // end AssumeRoleWithWebIdentityResult
              xmlsw.writeEndElement()

              // Response Metadata
              xmlsw.writeStartElement("ResponseMetadata")

              xmlsw.writeStartElement("RequestId")
              xmlsw.writeCharacters("GUID-MOST-LIKELY")
              xmlsw.writeEndElement()

              // Response Metadata
              xmlsw.writeEndElement()

              // end XML document// end XML document

              xmlsw.writeEndDocument
              xmlsw.flush
              xmlsw.close

              val xmlstr = writer.getBuffer.toString

              println(xmlstr)
              pureResponse(Ok(xmlstr))
            } else {
              s3req.accessType = RangerS3Authorizer.READ
              if (authorizer.checkPermission(s3req)) {
                client {
                  request
                }
              } else {
                pureResponse(Forbidden("Access denied"))
              }
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
