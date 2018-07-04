# S3GW

S3GW is a proxy to RadosGW/S3 (AWS/S3 on the roadmap) that applies Apache Ranger policies to requests to buckets. It 
is accompanied by its sister project RangerS3Plugin.

## Installation

To install `s3gw` you will need Scala. 

## Configuration

Hardcoded at the moment.

## Integration

- Ceph `docker run -d -p 80:80 -e CEPH_DEMO_UID=ceph-admin -e CEPH_DEMO_ACCESS_KEY=accesskey -e CEPH_DEMO_SECRET_KEY=secretkey -e RGW_CIVETWEB_PORT=80 -e NETWORK_AUTO_DETECT=4 --name rgwl ceph/daemon demo; sleep 10`
- Apache Ranger: Pickup the files from https://github.com/coheigea/testcases/tree/master/apache/docker/ranger and launch it by `docker-compose up`.
- Apache Atlas `docker pull bolkedebruin/atlas-for-docker` && `docker run --rm -p 21000:21000 --name atlas bolkedebruin/atlas-for-docker`

## Roadmap

* Tests
* Improved policy handling
* Ranger Audit
* Bucket Notifications 
* Lineage (Apache Atlas integration)
* STS (Receiving accesskeys from Redis/Kafka)