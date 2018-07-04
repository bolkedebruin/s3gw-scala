import java.util.Date

import org.apache.ranger.plugin.policyengine.{RangerAccessRequestImpl, RangerAccessResourceImpl, RangerAccessResult}
import org.apache.ranger.plugin.service.RangerBasePlugin

import scala.collection.JavaConverters

case class S3Request() {
  var path: String = ""
  var owner: String = ""
  var method : String = ""
  var accessType: String = ""
  var username: String = ""
  var userGroups: Array[String] = Array[String]()
  var clientIp: String = ""
  var remoteAddr: String = ""
  var fwdAddresses: Array[String] = Array[String]()
}

object RangerS3Authorizer {
  def WRITE     = s"write"
  def READ      = s"read"
  def WRITE_ACP = s"write_acp"
  def READ_ACP  = s"read_acp"

  private val plugin = new RangerBasePlugin("s3", "s3")

  plugin.init()

  def checkPermission(s3Request: S3Request): Boolean = {
    val request = new RangerAccessRequestImpl()
    val resource = new RangerAccessResourceImpl()

    resource.setValue("path", s3Request.path)
    resource.setOwnerUser(s3Request.owner)

    request.setResource(resource)
    request.setAccessTime(new Date)
    request.setUser(s3Request.username)
    request.setUserGroups(JavaConverters.setAsJavaSet(s3Request.userGroups.toSet))
    request.setClientIPAddress(s3Request.clientIp)
    request.setAccessType(s3Request.accessType)
    request.setAction(s3Request.method)
    request.setForwardedAddresses(JavaConverters.bufferAsJavaList(
      s3Request.fwdAddresses.toBuffer)
    )
    request.setRemoteIPAddress(s3Request.remoteAddr)

    val result: Option[RangerAccessResult ] = Option(plugin.isAccessAllowed(request))
    if (result.isDefined) {
      result.get.getIsAllowed
    } else {
      false
    }
  }
}
