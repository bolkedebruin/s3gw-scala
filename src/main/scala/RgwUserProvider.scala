import org.twonote.rgwadmin4j.{RgwAdmin, RgwAdminBuilder}

object RgwUserProvider {
  def apply(adminAccessKey: String,
            adminSecretKey: String,
            endpoint: String): RgwUserProvider =
    new RgwUserProvider(adminAccessKey, adminSecretKey, endpoint)
}

class RgwUserProvider(adminAccessKey: String, adminSecretKey: String, endpoint: String) {
  protected var key2user = collection.mutable.Map[String, String]()
  protected val rgwAdmin: RgwAdmin = new RgwAdminBuilder()
    .accessKey(adminAccessKey)
    .secretKey(adminSecretKey)
    .endpoint(endpoint)
    .build()


  def sync(): Unit = {
    println("Syncing users from ceph")
    key2user = collection.mutable.Map[String, String]()

    rgwAdmin.listUserInfo().forEach(
      user => {
        user.getS3Credentials.forEach(
          s3 => {
            key2user(s3.getAccessKey) = s3.getUserId
          }
        )
      }
    )
  }

  def getUser(accessKey: String): String = {
    key2user(accessKey)
  }
}
